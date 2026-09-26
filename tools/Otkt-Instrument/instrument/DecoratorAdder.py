import ast
'''
class DecoratorAdder(ast.NodeTransformer):
    """Adds @instrument decorator after all existing decorators."""
    def visit_FunctionDef(self, node):
        if not any(isinstance(d, ast.Name) and d.id == 'instrument' for d in node.decorator_list):
            node.decorator_list.append(ast.Name(id='instrument', ctx=ast.Load()))
        return node
'''



class DecoratorAdder(ast.NodeTransformer):
    """Adds @instrument decorator to functions not already instrumented."""
    
    def visit_FunctionDef(self, node):
        if not self._has_instrument_decorator(node.decorator_list):
            node.decorator_list.insert(0, ast.Name(id='instrument', ctx=ast.Load()))
        return node

    def _has_instrument_decorator(self, decorators):
        for decorator in decorators:
            # Direct: @instrument
            if isinstance(decorator, ast.Name) and decorator.id == 'instrument':
                return True
            # Call: @instrument(...)
            if isinstance(decorator, ast.Call):
                func = decorator.func
                if isinstance(func, ast.Name) and func.id == 'instrument':
                    return True
                if isinstance(func, ast.Attribute) and func.attr == 'instrument':
                    return True
            # Attribute: @module.instrument
            if isinstance(decorator, ast.Attribute) and decorator.attr == 'instrument':
                return True
        return False
