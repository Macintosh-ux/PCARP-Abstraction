import importlib, sys, inspect
from State import State

class ExternalResolver:
    def __init__(self, imports):
        self._imports = set(imports)
        
        self._default_imports = set(sys.stdlib_module_names) - {"this", "antigravity"}
        self._all_imports = self._default_imports | self._imports
        
        self._imported = self._import_modules()
        self._methods = self._list_external_methods()
      
    def _import_modules(self):
        """Import all modules in the predefined list, ignoring those that are not found."""
        return [importlib.import_module(module) for module in self._all_imports if self._safe_import(module)]

    def _safe_import(self, module_name):
        """Attempt to import a module safely, returning False if not found."""
        try:
            importlib.import_module(module_name)
            return True
        except ModuleNotFoundError:
            return False
            
    def _list_external_methods(self):
        """Extracts methods from all imported modules."""
        methods = {}
        for module in self._imported:
            module_methods = set()
            for type_name in dir(module):
                cls = getattr(module, type_name, None)
                if isinstance(cls, type):
                    module_methods.update(name for name, _ in inspect.getmembers(cls, predicate=inspect.isfunction))
            module_funcs = set(name for name in dir(module) if hasattr(module, name))   
            methods[module.__name__] = module_methods | module_funcs
        return methods
              
    def resolve_external_call(self, call):
        """Determines the origin of an external call and updates its state."""
        for module in self._imported:
            for i in self._methods.get(module.__name__, []):
                if i in call.callee.name:
                    call.update_callee_origin(module.__name__, module.__name__, State.IMPORTED)
                    return
