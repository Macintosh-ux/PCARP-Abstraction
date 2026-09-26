from CsvExporter import CsvExporter
from Stats import Stats

from generic.Search import Search

from call.CallResolver import CallResolver
from dataflow.DataflowResolver import DataflowResolver
from ExternalResolver import ExternalResolver

class Analysis:
    def __init__(self, source_dir, target_dir, mode, external, verbose):
        self.source_dir = source_dir  
        self.mode = mode  
        self.external = external 
        self.ext_resolver = None
        self.verbose = verbose

        self.exporter = CsvExporter(target_dir)
        self.search = Search(self.source_dir, verbose)
                             
    def run(self):
        if self.verbose:
            print("\n[INFO] Beginning of search")
        self.search.search()     
        if self.verbose:
            print("[INFO] End of search")
        
        if self.external:
            if self.verbose:
                print("[INFO] External activated")
            self.ext_resolver = ExternalResolver(self.search.imports)
        
        if self.mode == "call":
            self.call_analysis()
        elif self.mode == "dataflow":
            self.dataflow_analysis()
        elif self.mode == "both":
            self.call_analysis()
            self.dataflow_analysis()
                                      
    def call_analysis(self):
        if self.verbose:
            print("\n[INFO] Beginning of Call resolution")
        resolver = CallResolver(self.search, self.ext_resolver, self.verbose)
        resolver.resolve_all()     
        stats = Stats()
        stats.count_stats(resolver.opcalls)          
        stats.print_stats("Call")
        self.exporter.export_calls(resolver)
        if self.verbose:
            print("[INFO] End of Call resolution")
          
    def dataflow_analysis(self):
        if self.verbose:
            print("\n[INFO] Beginning of Dataflow resolution")
        resolver = DataflowResolver(self.search, self.ext_resolver, self.verbose)
        resolver.resolve_all()  
        stats = Stats()
        stats.count_stats(resolver.datacalls)          
        stats.print_stats("Dataflow")      
        self.exporter.export_dataflow(resolver)
        if self.verbose:
            print("[INFO] End of Dataflow resolution")
        