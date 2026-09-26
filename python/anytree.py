from anytree import Node, RenderTree, AsciiStyle, ContStyle
from anytree.iterators import PreOrderIter, PostOrderIter, LevelOrderIter, LevelOrderGroupIter
from anytree.search import find, findall, find_by_attr
from anytree.exporter import DictExporter, JsonExporter
from anytree.importer import DictImporter, JsonImporter

# Tree creation
root = Node("root")
a = Node("a", parent=root)
b = Node("b", parent=root)
c = Node("c", parent=a)
d = Node("d", parent=a)
e = Node("e", parent=b)
f = Node("f", parent=b)

# Tree rendering
print("RenderTree (AsciiStyle):")
for pre, fill, node in RenderTree(root, style=AsciiStyle()):
    print(f"{pre}{node.name}")

print("\nRenderTree (ContStyle):")
for pre, fill, node in RenderTree(root, style=ContStyle()):
    print(f"{pre}{node.name}")

# Traversal
print("\nPreOrderIter:")
print([node.name for node in PreOrderIter(root)])

print("\nPostOrderIter:")
print([node.name for node in PostOrderIter(root)])

print("\nLevelOrderIter:")
print([node.name for node in LevelOrderIter(root)])

print("\nLevelOrderGroupIter:")
for level in LevelOrderGroupIter(root):
    print([node.name for node in level])

# Search
print("\nfindall (name == 'b'):")
print([node.name for node in findall(root, filter_=lambda n: n.name == "b")])

print("\nfind (name == 'c'):")
print(find(root, filter_=lambda n: n.name == "c").name)

print("\nfind_by_attr (name == 'd'):")
print(find_by_attr(root, name="name", value="d").name)

# Node attributes
a.foo = "bar"
print("\nCustom attribute on node 'a':", a.foo)

# Export to dict and JSON (in-memory)
dict_exporter = DictExporter()
tree_dict = dict_exporter.export(root)
print("\nExported tree as dict:")
print(tree_dict)

json_exporter = JsonExporter(indent=2)
tree_json = json_exporter.export(root)
print("\nExported tree as JSON:")
print(tree_json)

# Import from dict and JSON (in-memory)
dict_importer = DictImporter()
new_tree_from_dict = dict_importer.import_(tree_dict)
print("\nImported tree from dict:")
for pre, fill, node in RenderTree(new_tree_from_dict, style=AsciiStyle()):
    print(f"{pre}{node.name}")

json_importer = JsonImporter()
new_tree_from_json = json_importer.import_(tree_json)
print("\nImported tree from JSON:")
for pre, fill, node in RenderTree(new_tree_from_json, style=AsciiStyle()):
    print(f"{pre}{node.name}")

# Tree navigation
print("\nParent of 'c':", c.parent.name)
print("Children of 'a':", [child.name for child in a.children])
print("Ancestors of 'd':", [ancestor.name for ancestor in d.ancestors])
print("Descendants of 'a':", [desc.name for desc in a.descendants])
print("Siblings of 'c':", [sib.name for sib in c.siblings])
print("Path from root to 'd':", [node.name for node in d.path])
