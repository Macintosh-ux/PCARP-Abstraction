import ast
from processor import transform

class MysteryFile:   
    def __init__(self, fullpath, name):
        self.path = fullpath
        self.name = name
    
    def instrument(self):
        with open(self.path, "r", encoding="utf-8") as f:
            original = f.read()

        transformed = transform(original)

        with open(self.path, "w", encoding="utf-8") as f:
            f.write(transformed)

    def is_instrumentable(self):
        return not (self._is_excluded_filename()
                    and self._is_non_executable_file)
    
    def _is_excluded_filename(self):
        excluded = (
            not self.name.endswith(".py") or
            "git" in self.name.lower() or
            "meson" in self.name.lower() or
            "cython" in self.name.lower() or
            "pythran" in self.name.lower() or
            self.name.endswith("_test.py") or
            self.name.startswith("test_") or
            self.name.startswith("generate_") or
            self.name == "conftest.py" or
            self.name.startswith("_") or
            self.name.startswith("__")
        )
        return excluded
 
    def _is_non_executable_file(self):
        """
        Returns True if the file is empty, contains only comments/docstrings,
        or lacks any function or method definitions.
        """
        try:
            with open(self.path, "r", encoding="utf-8") as f:
                source = f.read()
            if not source.strip():
                return True
            tree = ast.parse(source)
            has_function_defs = any(
                isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef))
                for node in ast.walk(tree)
            )
            return not has_function_defs
        except (SyntaxError, UnicodeDecodeError):
            return True
