package ForceGraphLayout;

import java.text.SimpleDateFormat;

public class Logger {
    //ansi color codes list \u001b
    //če ni static morš nrst new logger. če je static jo lahka sam nucš z callom
    private static final String RESET="\u001b[0m";
    private static final String RED="\u001b[31m";
    private static final String GREEN= "\u001b[32m";
    private static final String YELLOW= "\u001b[33m";
    private static final String BLUE= "\u001b[34m";
    private static final String MAGENTA ="\u001b[35m";
    private static final String CYAN= "\u001b[36m";
    private static final SimpleDateFormat dateFormat= new SimpleDateFormat ("yyy-MM-dd HH mm:ss.SSS");
    public static void log(String message){
        log(message, LogLevel.Info);
    }
    public static void log(String message,LogLevel level){
        //Unix timestamp
        String dateString=dateFormat.format(System.currentTimeMillis());
        String threadName = Thread.currentThread().getName();

        String messagePrefix="["+dateString+"]["+threadName+"]"+level+": ";
        //[2024-01-01-]...
        switch (level){
            case Info->messagePrefix =YELLOW+messagePrefix+RESET;
            case Debug->messagePrefix =MAGENTA+messagePrefix+RESET;
            case Error -> messagePrefix =RED+messagePrefix+RESET;
            case Warn -> messagePrefix =CYAN+messagePrefix+RESET;
            case Success -> messagePrefix =GREEN+messagePrefix+RESET;
            case Status -> messagePrefix =BLUE+messagePrefix+RESET;
        }
        System.out.println(messagePrefix+message);
    }
}
