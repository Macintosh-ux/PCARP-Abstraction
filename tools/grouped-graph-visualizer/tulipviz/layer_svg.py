import re
from lxml import etree as ET

STANDARD_BG_COLOR = "#f0f0f0"
AGGREGATED_BG_COLOR = "#dde5ff"
UNDER_THRESHOLD_BG_COLOR = "#ffc499"
ERROR_BG_COLOR = "#fae5e3"

# Only background colors belong here. Foreground colors are not rectangle fills.
COMPONENT_BG_COLORS = {
    STANDARD_BG_COLOR,
    AGGREGATED_BG_COLOR,
    UNDER_THRESHOLD_BG_COLOR,
    ERROR_BG_COLOR,
}


def normalize_color(color):
    """Converts named, short hexadecimal and rgb/rgba colors to #rrggbb."""
    if not color:
        return None

    color = color.strip().lower()

    named_colors = {
        "white": "#ffffff",
        "black": "#000000",
    }

    if color in named_colors:
        return named_colors[color]

    if re.fullmatch(r"#[0-9a-f]{3}", color):
        return "#" + "".join(character * 2 for character in color[1:])

    if re.fullmatch(r"#[0-9a-f]{6}", color):
        return color

    match = re.fullmatch(
        r"rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)"
        r"(?:\s*,\s*[^)]+)?\s*\)",
        color,
    )

    if not match:
        return color

    red, green, blue = map(int, match.groups())

    if any(channel < 0 or channel > 255 for channel in (red, green, blue)):
        return None

    return f"#{red:02x}{green:02x}{blue:02x}"


def get_style_property(element, property_name):
    """Returns one property from an inline SVG style declaration."""
    for declaration in element.attrib.get("style", "").split(";"):
        key, separator, value = declaration.partition(":")

        if separator and key.strip().lower() == property_name.lower():
            return value.strip()

    return None


def get_fill(element):
    """Returns the normalized fill from an SVG attribute or inline style."""
    fill = element.attrib.get("fill")

    if fill is None:
        fill = get_style_property(element, "fill")

    return normalize_color(fill)


def is_component_group(group, namespace):
    """
    Identifies the actual component group.

    Only rectangles belonging directly to this group are inspected. Package
    groups are therefore not classified as components merely because they
    contain component descendants.
    """
    rectangles = group.xpath(
        "./svg:rect",
        namespaces={"svg": namespace},
    )

    return any(
        get_fill(rectangle) in COMPONENT_BG_COLORS
        for rectangle in rectangles
    )


def contains_component_group(element, component_groups):
    """
    Returns whether an element is or contains an identified component group.

    This propagates component ordering through nested package and export
    wrappers, preventing an ancestor-level overlay from covering a component.
    """
    if element in component_groups:
        return True

    return any(
        descendant in component_groups
        for descendant in element.iterdescendants()
    )


def move_component_branches_to_front(root, namespace):
    """
    Moves component-containing branches to the end of every SVG parent.

    SVG elements appended later are painted later and therefore appear in
    front. Relative ordering inside both partitions remains stable.
    """
    groups = root.xpath(
        ".//svg:g",
        namespaces={"svg": namespace},
    )

    component_groups = {
        group
        for group in groups
        if is_component_group(group, namespace)
    }

    # Process deepest parents first so local ordering is preserved when their
    # complete wrapper branch is subsequently moved at a higher level.
    parents = sorted(
        {
            group.getparent()
            for group in component_groups
            if group.getparent() is not None
        }
        | {
            ancestor
            for group in component_groups
            for ancestor in group.iterancestors()
        },
        key=lambda element: len(list(element.iterancestors())),
        reverse=True,
    )

    for parent in parents:
        children = list(parent)

        component_branches = [
            child
            for child in children
            if contains_component_group(child, component_groups)
        ]

        # Re-appending only component branches keeps package backgrounds,
        # edges and labels in their original relative order.
        for child in component_branches:
            if child.getparent() is parent:
                parent.remove(child)
                parent.append(child)


def push_component_groups_to_end(svg_path, output_path=None):
    """
    Places component boxes above colored package backgrounds and other overlays.
    """
    parser = ET.XMLParser(remove_blank_text=True)
    tree = ET.parse(svg_path, parser)
    root = tree.getroot()
    namespace = root.nsmap.get(
        None,
        "http://www.w3.org/2000/svg",
    )

    move_component_branches_to_front(root, namespace)

    if output_path:
        tree.write(
            output_path,
            encoding="UTF-8",
            xml_declaration=True,
            pretty_print=True,
        )
        return output_path

    return ET.tostring(
        tree,
        encoding="unicode",
        pretty_print=True,
    )


# Backwards-compatible entry point used by the existing export pipeline.
def push_white_groups_to_end(svg_path, output_path=None):
    return push_component_groups_to_end(svg_path, output_path)