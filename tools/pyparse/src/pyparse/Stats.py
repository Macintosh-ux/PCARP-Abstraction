from termcolor import colored
from State import State

class Stats:
    def __init__(self):
        self.stats = {}
        self.stats["not_found"] = 0
        self.stats["imports"] = 0
        self.stats["class_definitions"] = 0
        self.stats["iter_vars"] = 0
        self.stats["generic"] = 0
        self.stats["params"] = 0
     
    def count_stats(self, calls):
        for call in calls:
            if call.callee.state == State.UNKNOWN:
                self.stats["not_found"] += 1
            elif call.callee.state == State.IMPORTED:
                self.stats["imports"] += 1
            elif call.callee.state == State.CLASS:
                self.stats["class_definitions"] += 1
            elif call.callee.state == State.ITERVAR:
                self.stats["iter_vars"] += 1
            elif call.callee.state == State.PARAM:
                self.stats["params"] += 1
            else:
                self.stats["generic"] += 1
                
        self.stats["total"] = len(calls)
        self.stats["found"] = self.stats["generic"] + self.stats["imports"] + self.stats["class_definitions"] + self.stats["iter_vars"] + self.stats["params"]
        
    def _rate(self, key):
        if self.stats['total'] == 0:
            return f"{(0):.2f}%" 
        return f"{(self.stats[key] / self.stats['total'] * 100):.2f}%" 
        
    def _stat(self, key):
        formatted_key = key.replace("_", " ").title() + ":"
        return f"{formatted_key:<20}{self.stats[key]:>10} ({self._rate(key)})"
      
    def print_stats(self, name):     
        print("=" * 40)
        print(f"{name} Statistics Overview")
        print("=" * 40)
        
        print(self._stat("total"))
        print(colored(self._stat("found"), "green"))
        print(colored(self._stat("not_found"), "red"))
        
        print("=" * 14 + " Breakdown " + "=" * 15)
        print(self._stat("imports"))
        print(self._stat("class_definitions"))
        print(self._stat("iter_vars"))
        print(self._stat("params"))
        print(self._stat("generic"))
       
        print("=" * 40)