from generic.Operation import Operation
from call.OperationCall import OperationCall
from generic.ClassInfo import ClassInfo
from generic.FuncInfo import FuncInfo
from State import State
import ast

class Context:
    def __init__(self):   
        self._class = None
        self._func = None     
        self._file = None
    
    def update_func(self, funcNode):
        self._func = funcNode
        
    def update_file(self, fileinfo):
        self._file = fileinfo

    def update_class(self, classInfo):
        self._class = classInfo
        
    @property
    def file(self):
        return self._file
    
    @property
    def cur_class_name(self):
        return self._class.name
                              
    def resolve_datacall_targets(self, datacall):
        if isinstance(datacall, ast.Assign):
            res = []
            for t in datacall.targets:
                match t:
                    case ast.Subscript():
                        res.append(t.value)  
                    case ast.Tuple():  
                        res.extend(t.elts)  
                    case _:
                        res.append(t)
            return res
        if isinstance(datacall.target, (ast.Subscript, ast.Name, ast.Attribute)):
            return [datacall.target]
        return [State.UNRESOLVED]
  
    def resolve_datacall_values(self, node):
        match node:
            case ast.List() | ast.Tuple() | ast.Set() if not node.elts:
                return [ast.Constant(State.EMPTY_COLLECTION)]
            case ast.List() | ast.Tuple() | ast.Set():
                return sum((self.resolve_datacall_values(el) for el in node.elts), [])
            case ast.Dict() if not node.keys:
                return [ast.Constant(State.EMPTY_COLLECTION)]
            case ast.Dict():
                return sum((self.resolve_datacall_values(k) + self.resolve_datacall_values(v) for k, v in zip(node.keys, node.values)), [])
            case ast.ListComp() | ast.SetComp() | ast.GeneratorExp() | ast.DictComp():
                return sum((self.resolve_datacall_values(gen.iter) for gen in node.generators), [])
            case ast.IfExp():
                return sum((self.resolve_datacall_values(n) for n in (node.test, node.body, node.orelse)), [])
            case ast.JoinedStr():
                return sum((self.resolve_datacall_values(val.value) for val in node.values if isinstance(val, ast.FormattedValue)), [])
            case ast.Lambda():
                return [ast.Constant(State.LAMBDA)]
            case ast.BinOp():
                return sum((self.resolve_datacall_values(n) for n in (node.left, node.right)), [])
            case ast.UnaryOp():
                return self.resolve_datacall_values(node.operand)
            case ast.Compare():
                return sum((self.resolve_datacall_values(n) for n in (node.left, *node.comparators)), [])
            case ast.BoolOp():
                return sum((self.resolve_datacall_values(val) for val in node.values), [])
            case _:
                return [node]
        
    def resolve_name(self, node):
        name_parts = [] 
        while isinstance(node, (ast.Attribute, ast.Call, ast.Subscript)):
            if isinstance(node, ast.Attribute):
                name_parts.append(node.attr)
            elif isinstance(node, ast.Subscript):
                name_parts.append(self.resolve_name(node.value))
            node = node.value if isinstance(node, (ast.Attribute, ast.Subscript)) else node.func    
        if isinstance(node, ast.Name):
            name_parts.append(node.id)   
        if isinstance(node, ast.ListComp):
            return State.COMP
        if not name_parts:
            return State.EMPTY          
        return ".".join(reversed(name_parts))
 
    def build_datacalls(self, datacall, parent):
        values = self.resolve_datacall_values(datacall.value)
        targets = self.resolve_datacall_targets(datacall)
        res = []
        for name in targets:
            caller = Operation(self._file.full_path, self._file.module, self.resolve_name(name), State.KNOWN)

            # Assign caller to the correct scope
            if isinstance(parent, ast.Module):
                self._file.add_global_var(caller)
            elif isinstance(parent, (ast.ClassDef, ast.FunctionDef)) and "__init__" in getattr(parent, "name", ""):
                self._class.add_attr(caller)

            # Process values
            for val in filter(lambda v: not isinstance(v, ast.Constant), values):
                resolved_name = self.resolve_name(val)
                if resolved_name == State.COMP:
                    continue
                if "self" in resolved_name:
                    resolved_name = self._class.name
                callee = Operation(State.UNKNOWN, State.UNKNOWN, resolved_name, State.UNKNOWN)
                res.append(OperationCall(caller, callee))
        return res
  
    def build_class(self, node):
        return ClassInfo(self._file.full_path, f"{self._file.module}.{node.name}", node.name, node.bases)
    
    def build_func(self, node):
        return FuncInfo(self._file.full_path, self._file.module, node)
    
    def build_iterator_var(self, name):
        if isinstance(name, ast.Tuple):
            return [Operation(self._file.full_path, self._file.module, self.resolve_name(el), State.ITERVAR) for el in name.elts]  
        return [Operation(self._file.full_path, self._file.module, self.resolve_name(name), State.ITERVAR)]
    
    def build_call(self, node):
        caller = Operation(
            self._file.full_path,
            self._file.module,
            self._file.base_name if self._func is None else self._func.name,
            State.UNKNOWN if self._func is None else State.KNOWN
        )
        callee = Operation(
            State.UNKNOWN,
            State.UNKNOWN,
            self.resolve_name(node),
            State.UNKNOWN
        )
        return OperationCall(caller, callee)
    
    def build_import_froms(self, node):
        return [Operation(State.IMPORTED, node.module, alias.name, State.IMPORTED)
               for alias in node.names] + \
            [Operation(State.IMPORTED, node.module, alias.asname, State.IMPORTED)
            for alias in node.names if alias.asname]
