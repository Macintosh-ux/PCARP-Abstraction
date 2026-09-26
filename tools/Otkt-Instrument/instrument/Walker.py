from MysteryFile import MysteryFile
import os, importlib

class Walker:
    EXCLUDED_DIRS = {"third_party", ".venv", "venv", "build", "dist", "__pycache__", ".eggs"}
    
    def __init__(self, directory, verbose=False, log_path="instrumentation_skips.log", log_output=False):
        self.SKIP_LOG_PATH = log_path
        self.log_output = log_output
        self.directory = directory
        self.verbose = verbose
        self.skipped = 0
        self.instrumented = 0
       
    def _can_resolve_instrument(self):
        return importlib.util.find_spec("otkt.tools.instrument") is not None

    def _log_skip(self, file_path):
        self.skipped += 1
        if self.log_output:
            with open(self.SKIP_LOG_PATH, "a", encoding="utf-8") as log_file:
                log_file.write(f"[SKIP] {file_path}\n")

    def _is_excluded_path(self, path):
        parts = os.path.normpath(path).split(os.sep)
        return any(
            part in self.EXCLUDED_DIRS or part.startswith("_")
            for part in parts
        )
       
    def walk(self):
        """Walk through the directory and transform eligible Python files."""
        if not self._can_resolve_instrument():
            raise Exception("otkt.tools.instrument package is not currently installed. Aborting.")
          
        for root, _, files in os.walk(self.directory):
            if self._is_excluded_path(root):
                self._log_skip(root)
                continue

            for file in files:
                full_path = os.path.join(root, file)
                mfile = MysteryFile(full_path, file)
                
                if not mfile.is_instrumentable():
                    self._log_skip(full_path)
                    continue
                
                self.instrumented += 1
                if self.verbose:
                    print(f"Instrumenting {full_path}")
                
                try:
                    mfile.instrument()
                except UnicodeDecodeError:
                    self._log_skip(full_path, "Unicode decode error")
                except OSError as e:
                    self._log_skip(full_path, f"OSError: {e}")


        print("=" * 40)
        print("Files processed.")
        print(f"{self.instrumented} instrumented files.")
        print(f"{self.skipped} skipped files.")
        if self.log_output:
            print(f"See {self.SKIP_LOG_PATH} for more information.")
        print("=" * 40)