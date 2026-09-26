import inspect
import types
from typing import get_args

def signature(func):
    return f'{scope(func)} {return_type(func)} {package(func)}.{fclass(func)}.{fname(func)}({params(func)})'

def scope(func):
    if func.__name__.startswith("__"):
        return "private"
    elif func.__name__.startswith("_"):
        return "protected"
    return "public"

def return_type(func):
    if func.__annotations__ and "return" in func.__annotations__.keys():
        return convert(func.__annotations__["return"])
    elif "return " in inspect.getsource(func):    
        return "Object"
    return "void"

def package(func):
    return func.__module__

def fclass(func):
    class_name = func.__qualname__.split(".")[0]
    if class_name == func.__name__:
        return func.__module__.split(".")[-1]
    return class_name

def fname(func):
    return func.__name__

def params(func):
    if func.__annotations__: 
        return params_type(func)
    return params_no_type(func)

def params_type(func):
    return ", ".join(convert(t) for t in func.__annotations__.values())
              
def params_no_type(func):
    parameters = inspect.signature(func).parameters
    return ", ".join("Object" for name in parameters if name != "self")
              
python_to_java = {
    "int": "int",
    "float": "double",
    "bool": "boolean",
    "str": "String",
    "list": "List<T>",
    "tuple": "Tuple<T1, T2>", # or List<T> for variable length
    "dict": "Map<K, V>",
    "set": "Set<T>",
    "None": "void",
    "bytes": "byte[]",
    "complex": "Pair<Double, Double>", # real & imaginary parts
    "object": "Object",
    "Callable": "Object", # functional Interface or lambda
    "Iterable": "Iterable<T>",
    "Generator": "Iterator<T>",
    "datetime": "Date"
}

def convert(t):
    if isinstance(t, str):
        return python_to_java.get(t, t)

    if t is None:
        return python_to_java.get("None", "Object")

    # Handle UnionType (like int | str)
    if isinstance(t, types.UnionType):
        return " | ".join(convert(arg) for arg in get_args(t))

    # Handle types without __name__
    type_name = getattr(t, '__name__', None)
    if type_name is None:
        return str(t)  # fallback: str representation

    return python_to_java.get(type_name, "Object")
