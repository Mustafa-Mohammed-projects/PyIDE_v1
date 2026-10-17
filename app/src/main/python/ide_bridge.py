"""PyIDE bridge between the Kotlin UI and Python (run / pip / REPL)."""
import builtins
import contextlib
import ctypes
import glob
import importlib
import importlib.abc
import importlib.util
import io
import os
import runpy
import shutil
import sys
import threading
import traceback

INDEX = "https://chaquo.com/pypi-13.1"
_state = {"io": None, "tid": None, "site": None, "cache": None, "abi": "arm64_v8a", "n": 0}
_console = None


def setup(site, cache, abi):
    _state.update(site=site, cache=cache, abi=abi.replace("-", "_"))
    os.makedirs(site, exist_ok=True)
    os.makedirs(cache, exist_ok=True)
    for p in glob.glob(os.path.join(cache, "*.png")):
        try:
            os.remove(p)
        except OSError:
            pass
    if site not in sys.path:
        sys.path.insert(0, site)
    os.environ["MPLBACKEND"] = "Agg"
    os.environ["MPLCONFIGDIR"] = os.path.join(cache, "mpl")
    os.environ["PYIDE_CACHE"] = cache
    if not any(isinstance(f, _Finder) for f in sys.meta_path):
        sys.meta_path.insert(0, _Finder())


# ---------------------------------------------------------------- streams
class _Out(io.TextIOBase):
    encoding = "utf-8"
    errors = "replace"

    def __init__(self, cb, err=False):
        self._cb, self._err = cb, err

    def writable(self):
        return True

    def write(self, s):
        if s:
            self._cb.write(str(s), self._err)
        return len(s)

    def flush(self):
        pass

    def isatty(self):
        return False


class _In(io.TextIOBase):
    encoding = "utf-8"

    def __init__(self, cb):
        self._cb = cb

    def readable(self):
        return True

    def readline(self, size=-1):
        line = self._cb.readLine()
        return "" if line is None else str(line) + "\n"

    def read(self, size=-1):
        return self.readline()

    def isatty(self):
        return False


def _input(prompt=""):
    if prompt:
        sys.stdout.write(str(prompt))
    line = sys.stdin.readline()
    if line == "":
        raise EOFError("EOF when reading a line")
    return line[:-1] if line.endswith("\n") else line


@contextlib.contextmanager
def _redirect(cb):
    _state["io"] = cb
    old = (sys.stdout, sys.stderr, sys.stdin, builtins.input)
    sys.stdout, sys.stderr, sys.stdin = _Out(cb), _Out(cb, True), _In(cb)
    builtins.input = _input
    try:
        yield
    finally:
        sys.stdout, sys.stderr, sys.stdin, builtins.input = old


# ---------------------------------------------------------------- images (matplotlib / OpenCV)
def _new_png(prefix):
    _state["n"] += 1
    return os.path.join(_state["cache"], "%s_%d.png" % (prefix, _state["n"]))


def _emit(path):
    cb = _state["io"]
    if cb is not None:
        cb.image(path)


def _flush_figures():
    plt = sys.modules.get("matplotlib.pyplot")
    if plt is None:
        return
    try:
        for num in plt.get_fignums():
            fig = plt.figure(num)
            p = _new_png("fig")
            fig.savefig(p, dpi=110, bbox_inches="tight", facecolor=fig.get_facecolor())
            _emit(p)
        plt.close("all")
    except Exception as e:  # noqa
        sys.stderr.write("[matplotlib] %s\n" % e)


def _patch_cv2():
    cv2 = sys.modules.get("cv2")
    if cv2 is None or getattr(cv2, "_pyide", False):
        return

    def imshow(name, img):
        p = _new_png("cv")
        cv2.imwrite(p, img)
        _emit(p)

    cv2.imshow = imshow
    cv2.waitKey = lambda *a, **k: -1
    cv2.destroyAllWindows = lambda *a, **k: None
    cv2.destroyWindow = lambda *a, **k: None
    cv2.namedWindow = lambda *a, **k: None
    cv2._pyide = True


def _patch_plt():
    plt = sys.modules.get("matplotlib.pyplot")
    if plt is None or getattr(plt, "_pyide", False):
        return
    plt.show = lambda *a, **k: _flush_figures()
    plt._pyide = True


_PATCHES = {"cv2": _patch_cv2, "matplotlib.pyplot": _patch_plt}


class _Wrap(importlib.abc.Loader):
    def __init__(self, inner, patch):
        self._inner, self._patch = inner, patch

    def create_module(self, spec):
        return self._inner.create_module(spec)

    def exec_module(self, module):
        self._inner.exec_module(module)
        try:
            self._patch()
        except Exception:  # noqa
            pass

    def __getattr__(self, name):
        return getattr(self._inner, name)


class _Finder(importlib.abc.MetaPathFinder):
    _busy = set()

    def find_spec(self, name, path=None, target=None):
        patch = _PATCHES.get(name)
        if patch is None or name in self._busy:
            return None
        self._busy.add(name)
        try:
            spec = importlib.util.find_spec(name)
        except Exception:  # noqa
            spec = None
        finally:
            self._busy.discard(name)
        if spec is not None and spec.loader is not None and hasattr(spec.loader, "exec_module"):
            spec.loader = _Wrap(spec.loader, patch)
            return spec
        return None


# ---------------------------------------------------------------- running code
def _execute(cb, target, argv, cwd):
    with _redirect(cb):
        _state["tid"] = threading.get_ident()
        old_cwd, old_path, old_argv = os.getcwd(), list(sys.path), sys.argv
        before = set(sys.modules)
        code = 0
        try:
            if cwd:
                os.chdir(cwd)
                sys.path.insert(0, cwd)
            sys.argv = list(argv)
            target()
        except SystemExit as e:
            c = e.code
            code = c if isinstance(c, int) else (0 if c is None else 1)
            if c is not None and not isinstance(c, int):
                print(c, file=sys.stderr)
        except KeyboardInterrupt:
            sys.stderr.write("\nKeyboardInterrupt\n")
            code = 130
        except BaseException as e:  # noqa
            tb = e.__traceback__
            while tb is not None and tb.tb_frame.f_code.co_filename.endswith(("runpy.py", "ide_bridge.py")):
                tb = tb.tb_next
            sys.stderr.write("".join(traceback.format_exception(type(e), e, tb)))
            code = 1
        finally:
            _flush_figures()
            _state["tid"] = None
            try:
                os.chdir(old_cwd)
            except OSError:
                pass
            sys.path[:] = old_path
            sys.argv = old_argv
            if cwd:
                for n in set(sys.modules) - before:
                    f = getattr(sys.modules.get(n), "__file__", None)
                    if f and f.startswith(cwd):
                        sys.modules.pop(n, None)
        return code


def run_file(path, cb, argv=None):
    args = [path] + (list(argv) if argv is not None else [])
    return _execute(cb, lambda: runpy.run_path(path, run_name="__main__"), args, os.path.dirname(path))


def run_module(name, cb, argv=None, cwd=None):
    args = [name] + (list(argv) if argv is not None else [])
    return _execute(cb, lambda: runpy.run_module(name, run_name="__main__", alter_sys=True), args, cwd)


def run_code(code, cb, cwd=None):
    return _execute(cb, lambda: exec(compile(code, "<string>", "exec"), {"__name__": "__main__"}), [""], cwd)


def stop():
    tid = _state["tid"]
    if tid is None:
        return False
    ctypes.pythonapi.PyThreadState_SetAsyncExc(ctypes.c_ulong(tid), ctypes.py_object(KeyboardInterrupt))
    return True


# ---------------------------------------------------------------- REPL
def repl_reset():
    global _console
    _console = None


def repl_push(line, cb):
    global _console
    if _console is None:
        import code
        _console = code.InteractiveConsole({"__name__": "__main__"})
    with _redirect(cb):
        _state["tid"] = threading.get_ident()
        try:
            return bool(_console.push(str(line)))
        finally:
            _flush_figures()
            _state["tid"] = None


# ---------------------------------------------------------------- pip
def _pip_main(args):
    from pip._internal.cli.main import main
    try:
        return int(main(list(args)) or 0)
    except SystemExit as e:
        return e.code if isinstance(e.code, int) else 1


def _install(rest):
    site, abi = _state["site"], _state["abi"]
    base = ["install", "--target", site, "--upgrade", "--disable-pip-version-check",
            "--no-warn-script-location", "--progress-bar", "off", "--extra-index-url", INDEX]
    v = "%d%d" % sys.version_info[:2]
    strict = base + ["--only-binary=:all:", "--implementation", "cp", "--python-version", v,
                     "--abi", "cp" + v, "--platform", "android_24_" + abi, "--platform", "android_21_" + abi]
    print("Resolving Android wheels (%s)..." % abi)
    rc = _pip_main(strict + rest)
    if rc != 0:
        print("\nRetrying with generic wheels / source packages...")
        rc = _pip_main(base + rest)
    importlib.invalidate_caches()
    print("\nDone." if rc == 0 else "\nFailed (exit %d)." % rc)
    return rc


def list_packages():
    import importlib.metadata as md
    if not _state["site"]:
        return ""
    out = []
    for d in md.distributions(path=[_state["site"]]):
        name = d.metadata["Name"]
        if name:
            out.append("%s==%s" % (name, d.version))
    return "\n".join(sorted(set(out), key=str.lower))


def _uninstall(name):
    import importlib.metadata as md
    key = name.lower().replace("_", "-").split("==")[0]
    site, found = _state["site"], False
    for d in list(md.distributions(path=[site])):
        if (d.metadata["Name"] or "").lower().replace("_", "-") == key:
            found = True
            for f in (d.files or []):
                try:
                    os.remove(os.path.join(site, str(f)))
                except OSError:
                    pass
    for dp, dn, _ in os.walk(site, topdown=False):
        if os.path.basename(dp) == "__pycache__":
            shutil.rmtree(dp, ignore_errors=True)
    for dp, _dn, _fn in os.walk(site, topdown=False):
        if dp != site and os.path.isdir(dp) and not os.listdir(dp):
            try:
                os.rmdir(dp)
            except OSError:
                pass
    importlib.invalidate_caches()
    return found


def pip(args, cb):
    args = [str(a) for a in args]
    with _redirect(cb):
        _state["tid"] = threading.get_ident()
        try:
            cmd = args[0] if args else "help"
            if cmd == "install":
                return _install(args[1:])
            if cmd == "uninstall":
                for n in [a for a in args[1:] if not a.startswith("-")]:
                    print(("Removed " if _uninstall(n) else "Not installed: ") + n)
                return 0
            if cmd in ("list", "freeze"):
                print(list_packages())
                return 0
            return _pip_main(args)
        except BaseException as e:  # noqa
            print("%s: %s" % (type(e).__name__, e), file=sys.stderr)
            return 1
        finally:
            _state["tid"] = None
