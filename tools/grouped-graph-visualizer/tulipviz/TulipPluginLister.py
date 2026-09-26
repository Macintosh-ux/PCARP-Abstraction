from tulip import tlp
import argparse

class TulipPluginLister:
    YELLOW = "\033[93m"
    END = "\033[0m"

    PLUGIN_TYPES = [
        ("path", "Plugins Directory", None),
        ("imports", "Import Plugins", tlp.getImportPluginsList),
        ("export", "Export Plugins", tlp.getExportPluginsList),
        ("algorithm", "Algorithm Plugins", tlp.getAlgorithmPluginsList),
        ("layout", "Layout Algorithm Plugins", tlp.getLayoutAlgorithmPluginsList),
        ("boolean", "Boolean Plugins", tlp.getBooleanAlgorithmPluginsList),
        ("color", "Color Plugins", tlp.getColorAlgorithmPluginsList),
        ("double", "Double Plugins", tlp.getDoubleAlgorithmPluginsList),
        ("integer", "Integer Plugins", tlp.getIntegerAlgorithmPluginsList),
        ("size", "Size Plugins", tlp.getSizeAlgorithmPluginsList),
        ("string", "String Plugins", tlp.getStringAlgorithmPluginsList)
    ]

    def __init__(self):
        self._setup_parser()
        self.args = self.parser.parse_args()

    def _setup_parser(self):
        self.parser = argparse.ArgumentParser(
            usage="python3 ListTulipPlugins.py [options...]",
            description="List available Tulip plugins"
        )
        for opt, label, _ in self.PLUGIN_TYPES:
            long_flag = f"--{opt}"
            self.parser.add_argument(long_flag, action="store_true", help=self._help_string(opt))
            
        self.parser.add_argument("-args", "--arguments", type=str, help="List the arguments of a given Tulip plugin")

    def _help_string(self, opt):
        return (
            "Print the path where Tulip plugins are stored"
            if opt == "path" else
            f"List available Tulip {opt} plugins"
        )

    def run(self):
        if self.args.arguments:
            self.print_args(self.args.arguments)
        else:
            self.list_plugins()

    def list_plugins(self):
        for opt, label, func in self.PLUGIN_TYPES:
            if getattr(self.args, opt):
                self._print_header(label)
                if func:
                    self._print_list(func())
                else:
                    print(tlp.TulipPluginsPath)
                    
    def print_args(self, plugin_name):
        self._print_dict(tlp.getDefaultPluginParameters(plugin_name))

    @staticmethod
    def _print_header(title):
        print(f"{TulipPluginLister.YELLOW}\n{title}:{TulipPluginLister.END}")

    @staticmethod
    def _print_list(items):
        if not items:
            print("Empty")
        else:
            for item in items:
                print(item)
                
    @staticmethod
    def _print_dict(items):
        if not items:
            print("Empty")
        else:
            max_key_length = max(len(key) for key in items.keys())
            for key in items.keys():
                print(f"{key:<{max_key_length}} : {items[key]}")
                

if __name__ == "__main__":
    TulipPluginLister().run()
