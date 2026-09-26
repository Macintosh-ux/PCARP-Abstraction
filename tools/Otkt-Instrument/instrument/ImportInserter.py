import ast
'''
class ImportInserter(ast.NodeTransformer):
    """Inserts 'from otkt.tools.instrument import instrument' after the last import."""
    def __init__(self):
        self.last_import_index = -1

    def visit_Module(self, node):
        # Find the index of the last import statement
        for idx, stmt in enumerate(node.body):
            if isinstance(stmt, (ast.Import, ast.ImportFrom)):
                self.last_import_index = idx

        # Build the 'from otkt.tools.instrument import instrument' node
        new_import = ast.ImportFrom(
            module="otkt.tools.instrument",
            names=[ast.alias(name="instrument", asname=None)],
            level=0
        )

        # Insert new import at the right position
        if self.last_import_index != -1:
            node.body.insert(self.last_import_index + 1, new_import)
        else:
            node.body.insert(0, new_import)

        return node
'''  

class ImportInserter(ast.NodeTransformer):
    """Inserts 'from otkt.tools.instrument import instrument' after the last import if not already present."""

    def __init__(self):
        self.last_import_index = -1
        self.has_instrument_import = False

    def visit_Module(self, node):
        for idx, stmt in enumerate(node.body):
            if isinstance(stmt, (ast.Import, ast.ImportFrom)):
                self.last_import_index = idx

                # Check for exact match
                if isinstance(stmt, ast.ImportFrom):
                    if stmt.module == "otkt.tools.instrument":
                        for alias in stmt.names:
                            if alias.name == "instrument":
                                self.has_instrument_import = True

        if not self.has_instrument_import:
            new_import = ast.ImportFrom(
                module="otkt.tools.instrument",
                names=[ast.alias(name="instrument", asname=None)],
                level=0
            )
            insert_index = self.last_import_index + 1 if self.last_import_index != -1 else 0
            node.body.insert(insert_index, new_import)

        return node
