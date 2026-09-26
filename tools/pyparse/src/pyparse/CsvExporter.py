import csv, sys, os

class CsvExporter:
    def __init__(self, target_dir):
        self.target = target_dir
              
    def _export_target(self, headers, data, filename): 
        if not os.path.isdir(self.target):
            os.mkdir(self.target)
        
        path = self.target + "/" + filename + ".csv"  
        with open(path, mode="w", newline="") as file:
            writer = csv.writer(file)
            writer.writerow(headers)
            writer.writerows(data)
        
    def _export_operation(self, ops):
        headers = ["file", "operation"]
        uncompressed = [op.export() for op in ops]
        self._export_target(headers, uncompressed, "operation_definitions")
        
    def _export_call_table(self, calls):
        headers = ["callerfilename",
                "callermodule",
                "callerfunction",
                "calleefilename",
                "calleemodule",
                "calleefunction"]
        uncompressed = [call.export() for call in calls]
        self._export_target(headers, uncompressed, "calltable")
        
    def _export_not_found(self, calls, name):
        headers = ["callerfilename",
                "callermodule",
                "caller",
                "callee"]
        uncompressed = [call.export_not_found() for call in calls if call.is_unresolved()]
        uncompressed = list(set(uncompressed))
        self._export_target(headers, uncompressed, "notfound_" + name)
              
    def _export_common_blocks(self, blocks):
        headers = ["name",
                "files",
                "modules",
                "variables"]
        uncompressed = []
        for block in blocks:
            uncompressed.extend(block.export())
        self._export_target(headers, uncompressed, "common-blocks")
              
    def _export_dataflow_cc(self, calls):
        headers = ["source-path",
                "source-module",
                "source-operation",
                "target-path",
                "target-module",
                "target-operation",
                "direction"]
        uncompressed = [call.export_with_direction() for call in calls]
        self._export_target(headers, uncompressed, "dataflow-cc")
    
    def _export_dataflow_cb(self, blocks):
        headers = ["common-block",
                "file",
                "module",
                "operation",
                "direction"]
        uncompressed = sum([block.export_dataflow_cb() for block in blocks], [])
        self._export_target(headers, uncompressed, "dataflow-cb")
              
    def export_calls(self, resolver):
        self._export_operation(resolver.ops)      
        self._export_call_table(resolver.opcalls)      
        self._export_not_found(resolver.opcalls, "calls")
        
    def export_dataflow(self, resolver):
        calls = resolver.datacalls
        blocks = resolver.common_blocks
    
        self._export_not_found(calls, "dataflow")
        self._export_dataflow_cc(calls)
        self._export_common_blocks(blocks)
        self._export_dataflow_cb(blocks)
            
        