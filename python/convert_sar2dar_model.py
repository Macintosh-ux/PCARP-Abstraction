"""
Python Script for Harmonizing Architecture Models: SAR to DAR Conversion

This script standardizes architecture model outputs by converting SAR-style outputs
into the DAR format.

Background:
    The SAR and DAR models differ in how they structure component identifiers:

    - SAR:
        * Uses the package name to encode the experiment name.
        * Embeds the full hierarchical path (e.g., 'module.submodule.component')
          into the component name.
        * Thus leading to each full name being prefixed with a dot ('.') when no experiment name is given.

    - DAR:
        * Treats package, module, and component names as distinct entities.
        * Ignores the experiment name entirely.
        * Does not prefix component names with a dot.

Problem:
    These structural differences make SAR outputs incompatible with DAR models.
    The prefixed dot and embedded experiment names in SAR prevent direct merging
    or comparison with DAR outputs.

Solution:
    This script performs the necessary transformation to align SAR outputs with
    DAR conventions, enabling seamless integration and analysis across both model types.
"""

from lxml import etree
import sys

def is_multi_part(string):
    """
    Returns True if the input string contains at least one dot,
    indicating a hierarchical name (e.g., 'a.b.c').
    """
    parts = string.split(".")
    return len(parts) > 1

# Ensure an input file is provided
if len(sys.argv) < 2:
    print("Usage: python3 convert.py <input_file.xml>")
    sys.exit(1)
xml_file = sys.argv[1]

# Parse the XML file with whitespace cleanup
parser = etree.XMLParser(remove_blank_text=True)
tree = etree.parse(xml_file, parser)
root = tree.getroot()

# Process each <componentTypes> element to split full name into package and name
for component in root.xpath("//componentTypes"):
    value = component.find("./value")
    if value is not None:
        full_name = value.get("name")
        if is_multi_part(full_name):       
            package = ".".join(full_name.split(".")[:-1])
        else:
            package = full_name
        name = full_name.split(".")[-1]
   
        value.set("package", package)
        value.set("name", name)

        # Ensure ComponentType identity is complete.
        if value.get("signature") is None:
            value.set("signature", full_name)

        if component.get("key") is None:
            component.set("key", full_name)

# Serialize and overwrite the original XML file
updated_file = xml_file
xml_string = etree.tostring(root, encoding="ASCII", xml_declaration=True, pretty_print=True)
with open(updated_file, "wb") as f:
    f.write(xml_string)

print(f"Updated XML saved to {updated_file}")
