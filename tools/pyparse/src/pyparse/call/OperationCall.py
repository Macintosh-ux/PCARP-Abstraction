class OperationCall:
    def __init__(self, caller, callee, direction="WRITE"):
        self.caller = caller
        self.callee = callee
        self.direction = direction

    def __repr__(self):
        return f"{repr(self.caller)} --> {repr(self.callee)}"
    
    def export(self):
        return (self.caller.path, 
                self.caller.module,
                self.caller.name,
                self.callee.path,
                self.callee.module,
                self.callee.name)
        
    def export_with_direction(self):
        return (self.caller.path, 
                self.caller.module,
                self.caller.name,
                self.callee.path,
                self.callee.module,
                self.callee.name,
                self.direction)
        
    def export_not_found(self):
        return (self.caller.path, self.caller.module, self.caller.name, self.callee.name)
    
    def update_callee_origin(self, file, module, state):
        if self.callee.is_unresolved():
            self.callee.update_origin(file, module, state)
            
    def is_unresolved(self):
        return self.callee.is_unresolved()