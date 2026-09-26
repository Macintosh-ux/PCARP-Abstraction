package tools.abstraction;

public class Settings {

    private String input_dir;
    private String output_dir;
    private String level;
    private String frst_opt_arg;
    private String scnd_opt_arg;
    private String thrd_opt_arg;
    private String frth_opt_arg;


    public Settings(String[] args){
        for (int i = 0; i < args.length; i += 2) {
            switch (args[i]) {
                case "-i":
                    this.input_dir = args[i + 1];
                    break;
                case "-o":
                    this.output_dir = args[i + 1];
                    break;
                case "-m":
                    this.level = args[i + 1];
                    this.frst_opt_arg = args[i + 2];
                    this.scnd_opt_arg = args[i + 3];
                    this.thrd_opt_arg = args[i + 4];
                    this.frth_opt_arg = args[i + 5];
                    break;
            }

        }
    }

    public String getInput_dir() {
        return input_dir;
    }

    public String getOutput_dir() {
        return output_dir;
    }

    public String getLevel() {
        return level;
    }

    public String getFrst_opt_arg() {
        return frst_opt_arg;
    }

    public String getScnd_opt_arg() {
        return scnd_opt_arg;
    }

    public String getThrd_opt_arg() {
        return thrd_opt_arg;
    }

    public String getFrth_opt_arg() {
        return frth_opt_arg;
    }
}