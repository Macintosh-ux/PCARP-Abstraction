class CallResolver():  
    def __init__(self, searcher, external=None, verbose=False):
        self._searcher = searcher
        self._external = external
        self._verbose = verbose
        
        self._opcalls = self._searcher.opcalls
        self._funcs = self._searcher.funcs
        self._classes = self._searcher.classes
        self._import_froms = self._searcher.import_froms
        
        # Make a list of all the involved operations (funcs + importfroms + classdefs)
        self._ops = [f.as_operation() for f in self._funcs] \
                    + self._import_froms \
                    + [c.as_operation() for c in self._classes]
                        
    @property
    def ops(self):
        return self._ops
    
    @property
    def opcalls(self):
        return self._opcalls
                    
    def resolve_all(self):      
        for i, opcall in enumerate(self._opcalls, start=1):
            if self._verbose:
                print(f"[INFO] [Call] Resolving {i}/{len(self._opcalls)}: {opcall}")            
            
            if opcall in self._funcs:
                continue
            elif opcall in self._classes:
                continue
            elif opcall in self._import_froms:
                continue
            elif self._external:
                self._external.resolve_external_call(opcall)     
        
        
    