from CommandArgs import CommandArgs
from Analysis import Analysis
             
if __name__ == "__main__": 
    args = CommandArgs()  

    analysis = Analysis(args.input_dir,
                        args.output_dir,
                        args.mode,
                        args.external,
                        args.verbose)
    analysis.run()
    
     
    
    
    

    


