from ImportInserter import ImportInserter
from DecoratorAdder import DecoratorAdder
import ast, re

def _extract_shebang(source_code):
    """Returns the shebang line (if any) and the remaining code."""
    lines = source_code.splitlines()
    if lines and re.match(r'^#!', lines[0]):
        return lines[0], '\n'.join(lines[1:])
    return "", source_code

def _transform_code(code_body):
    """Parses and transforms the code via AST."""
    tree = ast.parse(code_body)
    tree = DecoratorAdder().visit(tree)
    tree = ImportInserter().visit(tree)
    ast.fix_missing_locations(tree)
    return ast.unparse(tree)

def transform(source_code):
    """Coordinates all steps to transform code while preserving the shebang."""
    shebang_line, code_body = _extract_shebang(source_code)
    transformed_body = _transform_code(code_body)
    return f"{shebang_line}\n{transformed_body}" if shebang_line else transformed_body