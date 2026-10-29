"""VidDown runner: a thin wrapper around yt-dlp used by the Android app.

Usage: python viddown_runner.py '<json request>'
Output: one JSON object per line on file descriptor 1 (written with os.write so it
works even if sys.stdout is redirected, e.g. to logcat, on Android).
"""
import atexit
import json
import os
import re
import shutil
import sys
import time

ANSI = re.compile(r"\x1b\[[0-9;]*[A-Za-z]")


def emit(obj):
    obj = {k: v for k, v in obj.items() if v is not None}
    data = (json.dumps(obj, ensure_ascii=False) + "\n").encode("utf-8")
    while data:
        n = os.write(1, data)
        data = data[n:]


def clean(msg):
    msg = ANSI.sub("", str(msg)).strip()
    if msg.startswith("ERROR:"):
        msg = msg[6:].strip()
    return msg[:700]


class QuietLogger:
    def debug(self, msg):
        pass

    def info(self, msg):
        pass

    def warning(self, msg):
        pass

    def error(self, msg):
        pass


def base_opts(req):
    opts = {
        "quiet": True,
        "no_warnings": True,
        "noprogress": True,
        "noplaylist": True,
        "playlist_items": "1",
        "logger": QuietLogger(),
        "socket_timeout": 25,
        "retries": 5,
        "fragment_retries": 5,
        "cachedir": os.path.join(os.environ.get("HOME", "/tmp"), "ytdlp-cache"),
    }
    if req.get("ffmpeg"):
        opts["ffmpeg_location"] = req["ffmpeg"]
    cookies = req.get("cookies")
    if cookies and os.path.isfile(cookies):
        # Work on a throw-away copy: yt-dlp saves the jar back on exit.
        tmp = os.path.join(
            os.environ.get("TMPDIR", "/tmp"), "viddown-cookies-%d.txt" % os.getpid()
        )
        shutil.copyfile(cookies, tmp)
        atexit.register(lambda: os.path.exists(tmp) and os.remove(tmp))
        opts["cookiefile"] = tmp
    return opts


def fmt_size(n):
    if not n:
        return ""
    n = float(n)
    for unit in ("B", "KB", "MB", "GB"):
        if n < 1024 or unit == "GB":
            return f"{n:.0f} {unit}" if unit in ("B", "KB") else f"{n:.1f} {unit}"
        n /= 1024


def est_size(f, duration):
    if not f:
        return None
    size = f.get("filesize") or f.get("filesize_approx")
    if size:
        return int(size)
    tbr = f.get("tbr")
    if tbr and duration:
        return int(tbr * 1000 / 8 * duration)
    return None


def has_video(f):
    return f.get("vcodec") not in (None, "none")


def has_audio(f):
    return f.get("acodec") not in (None, "none")


def build_options(info):
    duration = info.get("duration")
    fmts = info.get("formats") or []
    videos = [f for f in fmts if has_video(f) and f.get("height")]
    audios = [f for f in fmts if has_audio(f) and not has_video(f)]

    best_audio = None
    if audios:
        best_audio = max(
            audios,
            key=lambda f: (f.get("ext") == "m4a", f.get("abr") or f.get("tbr") or 0),
        )
    audio_size = est_size(best_audio, duration)

    by_height = {}
    for f in videos:
        by_height.setdefault(int(f["height"]), []).append(f)

    options = []
    for h in sorted(by_height, reverse=True)[:7]:
        group = by_height[h]
        pool = [f for f in group if f.get("ext") == "mp4"] or group
        rep = max(pool, key=lambda f: (f.get("fps") or 0, f.get("tbr") or 0))
        size = est_size(rep, duration)
        if size and not has_audio(rep) and audio_size:
            size += audio_size
        fps = rep.get("fps") or 0
        label = f"{h}p" + (str(int(fps)) if fps >= 50 else "")
        detail = (rep.get("ext") or "mp4").upper()
        if size:
            detail += f" · ~{fmt_size(size)}"
        selector = (
            f"bv[height<={h}][ext=mp4]+ba[ext=m4a]/b[height<={h}][ext=mp4]/"
            f"bv[height<={h}]+ba/b[height<={h}]"
        )
        options.append({"id": f"v{h}", "label": label, "detail": detail, "selector": selector})

    if best_audio:
        detail = (best_audio.get("ext") or "m4a").upper()
        size = est_size(best_audio, duration)
        if size:
            detail += f" · ~{fmt_size(size)}"
        options.append(
            {"id": "audio", "label": "Audio only", "detail": detail,
             "selector": "ba[ext=m4a]/ba", "audio": True}
        )

    if not options:
        options.append(
            {"id": "best", "label": "Best available", "detail": "",
             "selector": "bv*+ba/b"}
        )
    return options


def first_entry(info):
    if info and info.get("entries"):
        entries = [e for e in info["entries"] if e]
        if not entries:
            raise ValueError("No video found at this link")
        return entries[0]
    return info


def cmd_info(req):
    import yt_dlp

    opts = base_opts(req)
    opts["skip_download"] = True
    with yt_dlp.YoutubeDL(opts) as ydl:
        info = first_entry(ydl.extract_info(req["url"], download=False))
    thumb = info.get("thumbnail")
    if not thumb and info.get("thumbnails"):
        thumb = info["thumbnails"][-1].get("url")
    emit({
        "type": "info",
        "title": info.get("title") or "Video",
        "uploader": info.get("uploader") or info.get("channel"),
        "duration": info.get("duration"),
        "thumbnail": thumb,
        "site": info.get("extractor_key") or info.get("extractor"),
        "options": build_options(info),
    })


def find_output(outdir):
    best, best_m = None, 0
    for name in os.listdir(outdir):
        if name.endswith((".part", ".ytdl", ".temp")):
            continue
        p = os.path.join(outdir, name)
        if os.path.isfile(p) and os.path.getmtime(p) >= best_m:
            best, best_m = p, os.path.getmtime(p)
    return best


def cmd_download(req):
    import yt_dlp

    outdir = req["outdir"]
    os.makedirs(outdir, exist_ok=True)
    state = {"idx": 0, "last": 0.0}

    def overall(d):
        info = d.get("info_dict") or {}
        parts = info.get("requested_formats") or [info]
        idx = state["idx"]
        done = d.get("downloaded_bytes") or 0
        total = d.get("total_bytes") or d.get("total_bytes_estimate") or 0
        sizes = [(p.get("filesize") or p.get("filesize_approx") or 0) for p in parts]
        if len(parts) > 1 and all(sizes):
            return min(99.0, (sum(sizes[:idx]) + done) / sum(sizes) * 100.0)
        if total:
            return min(99.0, (idx + done / total) / len(parts) * 100.0)
        return None

    def progress_hook(d):
        status = d.get("status")
        if status == "downloading":
            now = time.monotonic()
            if now - state["last"] < 0.4:
                return
            state["last"] = now
            emit({
                "type": "progress",
                "percent": overall(d),
                "speed": d.get("speed"),
                "eta": d.get("eta"),
            })
        elif status == "finished":
            state["idx"] += 1

    def pp_hook(d):
        name = d.get("postprocessor") or ""
        if d.get("status") == "started" and name.startswith(("Merger", "Fixup", "VideoRemuxer")):
            emit({"type": "merging", "name": name})

    opts = base_opts(req)
    opts.update({
        "format": req["format"],
        "merge_output_format": "mp4/mkv",
        "outtmpl": os.path.join(outdir, "%(title).100s [%(id)s].%(ext)s"),
        "progress_hooks": [progress_hook],
        "postprocessor_hooks": [pp_hook],
        "continuedl": True,
        "concurrent_fragment_downloads": 4,
        "updatetime": False,
    })
    with yt_dlp.YoutubeDL(opts) as ydl:
        info = first_entry(ydl.extract_info(req["url"], download=True))
        path = None
        for rd in info.get("requested_downloads") or []:
            if rd.get("filepath"):
                path = rd["filepath"]
                break
        if not path or not os.path.exists(path):
            path = find_output(outdir)
    if not path or not os.path.exists(path):
        raise RuntimeError("Download finished but the file was not found")
    emit({"type": "done", "path": path, "size": os.path.getsize(path)})


def main():
    try:
        req = json.loads(sys.argv[1])
        if req["cmd"] == "info":
            cmd_info(req)
        elif req["cmd"] == "download":
            cmd_download(req)
        else:
            raise ValueError("Unknown command")
    except SystemExit:
        raise
    except BaseException as e:  # noqa: BLE001
        emit({"type": "error", "message": clean(e) or e.__class__.__name__})
        try:
            import traceback
            traceback.print_exc()
        except Exception:
            pass
        sys.exit(1)


if __name__ == "__main__":
    main()
