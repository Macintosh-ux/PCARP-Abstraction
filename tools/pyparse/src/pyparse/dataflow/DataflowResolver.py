from State import *
from dataflow.CommonBlock import *

class DataflowResolver():  
    def __init__(self, searcher, external=None, verbose=False):
        self._searcher = searcher
        self._external = external
        self._verbose = verbose
        
        self._datacalls = self._searcher.datacalls
        self._funcs = self._searcher.funcs
        self._classes = self._searcher.classes
        self._import_froms = self._searcher.import_froms
        self._iterator_vars = self._searcher.iterator_vars
        self._files = self._searcher.files
        
        # Make a list of all the datacalls definitions
        self._data = list({call.caller for call in self._datacalls})
       
        # Make list of common blocks based on classes (attr) and files (global_vars)
        self._common_blocks = []
        to_parse = list(self._searcher.classes) + list(self._searcher.files)
        for elem in to_parse:
            b = CommonBlock(elem.name)
            b.vars.extend(elem.vars)
            if not b.empty():
                self._common_blocks.append(b)
                      
    @property             
    def common_blocks(self):
        return self._common_blocks
    
    @property
    def datacalls(self):
        return self._datacalls
    
    @property
    def data(self):
        return self._data
                                                                         
    def resolve_all(self):
        for i, call in enumerate(self._datacalls, start=1):
            if self._verbose:
                print(f"[INFO] [Dataflow] Resolving {i}/{len(self._datacalls)}: {call}")
                           
            if call in self._data:
                continue
            elif call in self._funcs:
                continue
            elif call in self._files:
                continue
            elif call in self._classes:
                continue
            elif call in self._import_froms:
                continue
            elif call in self._iterator_vars:
                continue
            elif self._external:
                self._external.resolve_external_call(call)        
        
        
        
    