def dump_list(list):
    for el in list:
        print(el)

def dump_default_dict(ddict):
    max_length = max(len(el) for el in ddict)
    for el in ddict:
        print(f"{el:<{max_length}} : {ddict[el]!r}")
        
def dump_dict(dict):
    for el in dict:
        print(str(el) + ":\n" + str(dict[el]))