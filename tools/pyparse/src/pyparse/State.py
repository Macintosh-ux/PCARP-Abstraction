class State:
    KNOWN = "++known++"
    FOUND = "++found++"
    CLASS = "++class++"
    PARAM = "++param++"
    ITERVAR = "++iter-var++"
    IMPORTED = "++imported++"
    
    UNKNOWN = "++unknown-component++"
    LAMBDA = "++lambda++"
    EMPTY_COLLECTION = "++empty-collection++"
    EMPTY = "++empty++"
    COMP = "++comp++"
    
    _KNOWN_STATES = [IMPORTED, FOUND, CLASS, PARAM, ITERVAR, IMPORTED]
    
    @staticmethod
    def isknown(state):
        return state in State._KNOWN_STATES