package banking;

import org.sqlite.SQLiteDataSource;
import java.sql.*;
import java.util.*;

class CardNumber{
    private final int IssuerIdentificationNumber;
    private final Long AccountIdentifier;
    private int Checksum;
    static int userIdLength = 9;
    static int IIN = 400000;

    public CardNumber() {
        this.IssuerIdentificationNumber = IIN;
        long id;
        id = Main.randLongNumber(userIdLength);
        this.AccountIdentifier = id;
        this.Checksum = 0;
        this.Checksum = Main.checkLuhn(getCardNumber());
    }
    public String getCardNumber() {
        String regExpr = String.format("%%d%%0%dd%%d",userIdLength);
        /*System.out.println(regExpr);*/
        return String.format(regExpr, IssuerIdentificationNumber, AccountIdentifier, Checksum);
    }

}

class Account {
    private CardNumber cardNumber;
    private final long passwd;
    static int PINLen = 4;
    private static String tableName;
    private static final HashMap<String, Long> accountMap = new HashMap<>();
    static SQLiteDataSource dbSrc = new SQLiteDataSource();
    static int AttemptTimes = 1;
    static public void setDbSrc(String filePath) {
        String dbUrl = "jdbc:sqlite:";
        dbSrc.setUrl(dbUrl + filePath);
    }

    static public void createTable(String name) {
        tableName = name;
        String attrListScript = "id INTEGER PRIMARY KEY AUTOINCREMENT, number TEXT, pin TEXT, balance INTEGER DEFAULT 0";
        try (Connection conn = dbSrc.getConnection()) {
            Statement state = conn.createStatement();
            try {
                state.execute(String.format("CREATE TABLE %s (%s)", tableName, attrListScript));
            } catch (SQLException ignored){

                /* 把所有的卡号从数据库写出到表里 */
                try {
                    ResultSet resultSet = state.executeQuery(String.format("SELECT number,pin FROM %s;", tableName));
                    while (resultSet.next()) {
                        String cardNumber = resultSet.getString("number");
                        Long pin = resultSet.getLong("pin");
                        accountMap.put(cardNumber, pin);
                    }
                } catch (SQLException throwable) {
                    throwable.printStackTrace();
                    System.exit(throwable.getErrorCode());
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.exit(0);
        }
    }

    private Account() {
        String s;

        /*System.out.println(accountMap);*/
        /*System.out.println("AccountMap Size: " + accountMap.size());*/

        do {
            cardNumber = new CardNumber();
            s = cardNumber.getCardNumber();
        } while (accountMap.containsKey(s));
        passwd = Main.randLongNumber(PINLen);
        accountMap.put(s, passwd);

        /* write into SQLite */
        try (Connection conn = dbSrc.getConnection()){
            Statement statement = conn.createStatement();

            try {
                int lines = statement.executeUpdate(String.format("INSERT INTO %s ('number', 'pin') VALUES (%s, %d)",
                        tableName,
                        s,
                        passwd
                ));
                /*System.out.println("add account affect line: " + lines);*/
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(0);
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        }

        System.out.println("\nYour card has been created");
        printAllInfo();
    }

    private void printAllInfo() {
        String regExpr = String.format("Your card number:\n" +
                                       "%%s\n" +
                                       "Your card PIN:\n" +
                                       "%%0%dd\n" +
                                       "\n", PINLen);
        /*System.out.println(regExpr);*/
        System.out.printf(regExpr, cardNumber.getCardNumber(), passwd);
    }

    public static void createAccount() {
        new Account();
    }

    public static void logIntoAccount() {
        Scanner scanner = new Scanner(System.in);
        System.out.println();

        for (int i = 0; i < AttemptTimes; i++) {
            System.out.println("Enter your card number:");
            String cardNumber = scanner.nextLine();
            System.out.println("Enter your PIN:");
            String PIN = scanner.nextLine();
            if (checkAccountAndPasswd(cardNumber, PIN)){
                accountConsole(cardNumber);
//                System.out.println("queryRtnCode: " + rtnCode);
                break;
            }
        }
//        System.out.println("logInRtnCode: " + rtnCode);
    }

    private static boolean checkAccountAndPasswd(String cardNumber, String pin) {
        long PIN;
        System.out.println();
        String msg = "Wrong card number or PIN!\n";
        try {
            PIN = Integer.parseInt(pin);
            if (Main.validateCardNumber(cardNumber) && accountMap.get(cardNumber) == PIN) {
                msg = "You have successfully logged in!\n";
                return true;
            }
        } catch (Exception ignored){
        } finally {
            System.out.println(msg);
        }
        return false;
    }

    private static long queryBalance(PreparedStatement queryBalance, String cardNumber) throws SQLException {
        long accountMoney = 0;
        queryBalance.setObject(1, cardNumber);
        ResultSet resultSet = queryBalance.executeQuery();
        while (resultSet.next()) {
            accountMoney = resultSet.getLong("balance");
        }

        return accountMoney;
    }

    private static void accountConsole(String cardNumber) {
        Scanner scanner = new Scanner(System.in);
        String QueryBalanceScript = String.format("SELECT balance FROM %s WHERE number = ?", tableName);
        String BalanceTransferScript = String.format("UPDATE %s SET balance = ? WHERE number = ?", tableName);
        String AccountRemoveScript = String.format("DELETE FROM %s WHERE number = ?", tableName);

        try (Connection conn = dbSrc.getConnection()) {
            if (conn.getAutoCommit()) {
                conn.setAutoCommit(false);
            }
            PreparedStatement queryBalance = conn.prepareStatement(QueryBalanceScript);
            PreparedStatement balanceTransfer = conn.prepareStatement(BalanceTransferScript);
            PreparedStatement accountRemove = conn.prepareStatement(AccountRemoveScript);
            long accountMoney;
            long targetMoney, money;

            while (true) {
                System.out.print("1. Balance\n" +
                                   "2. Add income\n" +
                                   "3. Do transfer\n" +
                                   "4. Close account\n" +
                                   "5. Log out\n" +
                                   "0. Exit\n");
                int code = Main.DefaultCode;
                try {
                    code = Integer.parseInt(scanner.nextLine());
                } catch (Exception ignored) {
                }
                System.out.println();

                switch (code) {

                    /* TODO SHOW BALANCE */
                    case 1:
                        try {
                            accountMoney = queryBalance(queryBalance, cardNumber);
                            System.out.printf("Balance: %d\n\n", accountMoney);
                        } catch (Exception e) {
                            System.err.println(e.getMessage());
                            System.out.println("Failed querying balance!");
                        }
                        break;

                    /* TODO INCREASE BALANCE */
                    case 2:
                        System.out.println("Enter income:");
                        try {
                            money = Long.parseLong(scanner.nextLine());
                            accountMoney = queryBalance(queryBalance, cardNumber);
                        } catch (Exception e) {
                            System.err.println(e.getMessage());
                            break;
                        }

                        try {
                            balanceTransfer.setObject(1, accountMoney + money);
                            balanceTransfer.setObject(2, cardNumber);
                            int line = balanceTransfer.executeUpdate();
                            /*System.out.printf("Affect line: %d%n", line);*/
                            System.out.println("Income was added!\n");
                        } catch (SQLException e) {
                            System.err.println(e.getMessage());
                        }
                        break;

                    /* TODO TRANSFER MONEY */
                    case 3:
                        System.out.println("Transfer");
                        System.out.println("Enter card number:");
                        String targetNumber;
                        try{
                            targetNumber = scanner.nextLine();
                        } catch (Exception e) {
                            System.err.println(e.getMessage());
                            break;
                        }

                        if (!Main.validateCardNumber(targetNumber)) {
                            System.out.println("Probably you made mistake in the card number. Please try again!\n");
                            break;
                        }
                        if (targetNumber.equals(cardNumber)) {
                            System.out.println("You can't transfer money to the same account!\n");
                            break;
                        }
                        queryBalance.setObject(1, targetNumber);
                        if (!queryBalance.executeQuery().next()) {
                            System.out.println("Such a card does not exist.\n");
                            break;
                        }
                        System.out.println("Enter how much money you want to transfer:");
                        try {
                            accountMoney = queryBalance(queryBalance, cardNumber);
                            money = Long.parseLong(scanner.nextLine());
                            if (money < 0) {
                                break;
                            }
                            if (money > accountMoney) {
                                System.out.println("Not enough money!\n");
                                break;
                            }
                            balanceTransfer.setObject(1, accountMoney - money);
                            balanceTransfer.setObject(2, cardNumber);
                            balanceTransfer.executeUpdate();
                            targetMoney = queryBalance(queryBalance, targetNumber);
                            balanceTransfer.setObject(1, targetMoney + money);
                            balanceTransfer.setObject(2, targetNumber);
                            balanceTransfer.executeUpdate();
                            System.out.println("Success!\n");
                        } catch (Exception e) {
                            System.err.println(e.getMessage());
                        }

                        break;

                    /* TODO DELETE ACCOUNT */
                    case 4:
                        accountMap.remove(cardNumber);
                        try {
                            accountRemove.setObject(1, cardNumber);
                            accountRemove.executeUpdate();
                            conn.commit();
                            System.out.println("The account has been closed!\n");
                        } catch (SQLException e) {
                            System.err.println(e.getMessage());
                        }
                        Main.main(Main.sysArgs);
                        break;

                    /* TODO√ LOG OUT */
                    case 5:
                        System.out.println("You have successfully logged out!");
                        System.out.println();
                        Main.main(Main.sysArgs);
                        break;

                    /* TODO√ EXIT SYSTEM*/
                    case 0:
                        System.out.println("Bye!");
                        System.exit(0);
                }

                conn.commit();
            }
        } catch (Exception e) {
                e.printStackTrace();
                System.exit(-1);
        }
    }
}



public class Main {
    public static int DefaultCode = 999;
    public static String[] sysArgs;
    public static void main(String[] args) {
        if (args.length != 2 || !args[0].equals("-fileName")) {
            System.out.println("Usage -fileName [PATH-TO-DATABASE]");
            return;
        }
        sysArgs = args;
        Account.setDbSrc(args[1]);
        Account.createTable("card");

        Scanner scanner = new Scanner(System.in);
        while(true) {
            System.out.println("1. Create an account\n2. Log into account\n0. Exit");
            String s = scanner.nextLine();
            int code = DefaultCode;
            try {
                code = Integer.parseInt(s);
            } catch (Exception ignored) {
            }
//            System.out.println(args.length);
            switch (code) {
                case 1:
                    Account.createAccount();
                    break;
                case 2:
                    Account.logIntoAccount();
                    break;
                case 0:
                    System.out.println("\nBye!");
                    System.exit(0);
            }
        }
    }

    public static long randLongNumber(int length) {
        Random random = new Random();
        long rtn = 0;
        for (int i = 0; i < length; i++) {
            rtn = rtn * 10 + random.nextInt(10);
        }
        return rtn;
    }

    public static int checkLuhn(String cardNumber) {
        int rtn = 0;
        for (int i = 0; i < cardNumber.length() - 1; i++) {
            int num;
            try {
                num = Integer.parseInt(cardNumber.substring(i, i + 1));
            } catch (Exception ignored) {
                return 99;
            }
            if (i % 2 == 0) {
                num = num * 2 > 9? num * 2 - 9: num * 2;
            }
            rtn += num;
            /*System.out.println(rtn);*/
        }
        return (10 - rtn % 10) % 10;
    }

    public static boolean validateCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.isEmpty()) {
            return false;
        }
        int last = -1;
        try {
            last = Integer.parseInt(cardNumber.substring(cardNumber.length() - 1));
        } catch (Exception ignored) {
        }
        return last == checkLuhn(cardNumber);
    }
}
