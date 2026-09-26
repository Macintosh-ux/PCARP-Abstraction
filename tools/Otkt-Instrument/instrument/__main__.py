from CommandArgs import CommandArgs
from Walker import Walker

if __name__ == "__main__":
    args = CommandArgs()
    walker = Walker(args.input_dir, verbose=args.verbose, log_output=args.log)
    walker.walk()