import java.sql.*;
import java.util.*;

public class Main {

    private static final Scanner sc = new Scanner(System.in);

    // Change this if your database file has a different name
    private static final String DB_URL = "jdbc:sqlite:identifier.sqlite";

    public static void main(String[] args) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            System.out.println("Connected to UFC database.");

            while (true) {
                printMainMenu();
                String choice = sc.nextLine().trim();

                if (choice.equals("1")) {
                    predictMatchup(conn);
                } else if (choice.equals("2")) {
                    viewTable(conn);
                } else if (choice.equals("3")) {
                    System.out.println("Exiting program...");
                    break;
                } else {
                    System.out.println("Invalid choice. Please input 1, 2, or 3.");
                }
            }

        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
        }
    }

    private static void printMainMenu() {
        System.out.println("\n===== UFC Moneyball Analytics =====");
        System.out.println("1. Predict matchup");
        System.out.println("2. View fighter table");
        System.out.println("3. Exit");
        System.out.print("Choose an option: ");
    }

    private static void predictMatchup(Connection conn) {
        try {
            Fighter fighter1 = null;
            Fighter fighter2 = null;

            while (fighter1 == null) {
                System.out.print("Enter first fighter name: ");
                String fighter1Name = sc.nextLine().trim();
                fighter1 = getFighter(conn, fighter1Name);

                if (fighter1 == null) {
                    System.out.println("Fighter not found in database. Please try again.");
                }
            }

            while (fighter2 == null) {
                System.out.print("Enter second fighter name: ");
                String fighter2Name = sc.nextLine().trim();
                fighter2 = getFighter(conn, fighter2Name);

                if (fighter2 == null) {
                    System.out.println("Fighter not found in database. Please try again.");
                }
            }

            System.out.println("\n===== Fighter Stats =====");
            printFighterComparison(fighter1, fighter2);

            double score1 = calculateFighterScore(fighter1, fighter2);
            double score2 = calculateFighterScore(fighter2, fighter1);

            double total = score1 + score2;
            double probability1 = total == 0 ? 50 : (score1 / total) * 100;
            double probability2 = total == 0 ? 50 : (score2 / total) * 100;

            Fighter winner;
            Fighter loser;
            double certainty;

            if (probability1 >= probability2) {
                winner = fighter1;
                loser = fighter2;
                certainty = probability1;
            } else {
                winner = fighter2;
                loser = fighter1;
                certainty = probability2;
            }

            String method = predictMethod(winner, loser);
            String explanation = generateExplanation(winner, loser);

            System.out.println("\n===== Prediction =====");
            System.out.println("Predicted winner: " + winner.name);
            System.out.printf("Certainty: %.2f%%\n", certainty);
            System.out.println("Predicted method: " + method);
            System.out.println("Explanation:");
            System.out.println(explanation);

        } catch (SQLException e) {
            System.out.println("Error predicting matchup: " + e.getMessage());
        }
    }

    private static Fighter getFighter(Connection conn, String name) throws SQLException {
        String sql = """
                SELECT 
                    fighter_name,
                    wins,
                    losses,
                    height,
                    weight,
                    reach,
                    stance,
                    dob,
                    slpm,
                    str_acc,
                    sapm,
                    str_def,
                    td_avg,
                    td_acc,
                    td_def,
                    sub_avg
                FROM fighters
                WHERE LOWER(fighter_name) LIKE LOWER(?)
                ORDER BY wins DESC
                LIMIT 1
                """;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, "%" + name + "%");
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return new Fighter(
                        rs.getString("fighter_name"),
                        rs.getInt("wins"),
                        rs.getInt("losses"),
                        rs.getString("height"),
                        rs.getString("weight"),
                        rs.getString("reach"),
                        rs.getString("stance"),
                        rs.getString("dob"),
                        rs.getDouble("slpm"),
                        parsePercent(rs.getString("str_acc")),
                        rs.getDouble("sapm"),
                        parsePercent(rs.getString("str_def")),
                        rs.getDouble("td_avg"),
                        parsePercent(rs.getString("td_acc")),
                        parsePercent(rs.getString("td_def")),
                        rs.getDouble("sub_avg")
                );
            }
        }

        return null;
    }

    private static void viewTable(Connection conn) {
        System.out.println("\n===== Fighter Table =====");
        runPreparedSQLAndPrint(getBaseFighterTableSQL() + " ORDER BY fighter_name", new ArrayList<>(), conn);

        System.out.println("\nPress ENTER to return to the main menu.");
        sc.nextLine();
    }

    private static String getBaseFighterTableSQL() {
        return """
                SELECT 
                    fighter_name,
                    wins,
                    losses,
                    height,
                    weight,
                    reach,
                    stance,
                    dob,
                    slpm,
                    str_acc,
                    sapm,
                    str_def,
                    td_avg,
                    td_acc,
                    td_def,
                    sub_avg
                FROM fighters
                """;
    }

    private static double calculateFighterScore(Fighter fighter, Fighter opponent) {
        double winRate = fighter.getWinRate();
        int totalFights = fighter.wins + fighter.losses;

        double strikingScore =
                (fighter.slpm * 10)
                        + (fighter.strAcc * 0.4)
                        + (fighter.strDef * 0.25)
                        - (fighter.sapm * 6);

        double grapplingScore =
                (fighter.tdAvg * 12)
                        + (fighter.tdAcc * 0.35)
                        + (fighter.tdDef * 0.20)
                        + (fighter.subAvg * 10);

        double defenseScore =
                (fighter.strDef * 0.5)
                        + (fighter.tdDef * 0.5)
                        - (fighter.sapm * 4);

        double experienceScore =
                (winRate * 0.7)
                        + Math.min(totalFights * 1.5, 30);

        double weightDifference =
                extractNumber(fighter.weight) - extractNumber(opponent.weight);

        double reachAdvantage =
                extractNumber(fighter.reach) - extractNumber(opponent.reach);

        double weightScore = calculateWeightScore(weightDifference);

        double physicalScore =
                weightScore + (reachAdvantage * 2.0);

        physicalScore = Math.max(-1000, Math.min(1000, physicalScore));

        double styleMatchupBonus = calculateStyleMatchupBonus(fighter, opponent);

        double finalScore =
                (0.15 * strikingScore)
                        + (0.15 * grapplingScore)
                        + (0.10 * defenseScore)
                        + (0.10 * experienceScore)
                        + (0.35 * physicalScore)
                        + (0.15 * styleMatchupBonus);

        return Math.max(finalScore, 1);
    }

    private static double calculateWeightScore(double weightDifference) {
        double absDifference = Math.abs(weightDifference);

        if (absDifference < 8) {
            return weightDifference * 1.0;
        }

        double WeightScore =
                Math.pow(absDifference, 1.5) * 0.9;

        return Math.signum(weightDifference) * WeightScore;
    }

    private static double calculateStyleMatchupBonus(Fighter fighter, Fighter opponent) {
        double bonus = 0;

        if (fighter.tdAvg >= 3.0 && opponent.tdDef < 60) {
            bonus += 20;
        }

        if (fighter.slpm >= 5.0 && opponent.strDef < 55) {
            bonus += 15;
        }

        if (fighter.subAvg >= 1.5 && opponent.tdDef < 60) {
            bonus += 15;
        }

        if (fighter.sapm > 4.5) {
            bonus -= 10;
        }

        return bonus;
    }

    private static String predictMethod(Fighter winner, Fighter loser) {
        double weightDifference =
                extractNumber(winner.weight) - extractNumber(loser.weight);

        if (weightDifference >= 100) {
            return "KO/TKO due to overwhelming size and power advantage";
        }

        if (weightDifference >= 60) {
            if (winner.tdAvg >= 2.0 || loser.tdDef < 60) {
                return "TKO by ground-and-pound/control";
            }

            return "KO/TKO due to major physical advantage";
        }

        if (weightDifference >= 30 && winner.slpm >= 4.0) {
            return "KO/TKO due to power and size advantage";
        }

        if (winner.subAvg >= 1.5 && loser.tdDef < 60) {
            return "Submission";
        }

        if (winner.slpm >= 5.0 && winner.strAcc >= 45 && loser.strDef < 55) {
            return "KO/TKO";
        }

        if (winner.tdAvg >= 3.0 && loser.tdDef < 60) {
            return "Decision by grappling/control";
        }

        return "Decision";
    }

    private static String generateExplanation(Fighter winner, Fighter loser) {
        String winnerArchetype = determineArchetype(winner);
        String loserArchetype = determineArchetype(loser);

        String winnerAdvantages = getAdvantages(winner, loser);
        String loserAdvantages = getAdvantages(loser, winner);

        String winnerDisadvantages = getDisadvantages(winner, loser);
        String loserDisadvantages = getDisadvantages(loser, winner);

        double weightDifference = extractNumber(winner.weight) - extractNumber(loser.weight);
        double weightScore = calculateWeightScore(weightDifference);

        String explanation = "";

        explanation += winner.name + " is predicted to win.\n";
        explanation += winner.name + " is classified as a " + winnerArchetype + ".\n";
        explanation += loser.name + " is classified as a " + loserArchetype + ".\n";
        explanation += winner.name + "'s advantages: " + winnerAdvantages + ".\n";
        explanation += winner.name + "'s disadvantages: " + winnerDisadvantages + ".\n";
        explanation += loser.name + "'s advantages: " + loserAdvantages + ".\n";
        explanation += loser.name + "'s disadvantages: " + loserDisadvantages + ".\n";

        if (weightDifference >= 100) {
            explanation += "The weight gap is extreme: " + winner.name + " is about "
                    + String.format("%.0f", weightDifference)
                    + " lbs heavier, so the model treats size as the dominant factor in power, durability, and grappling control.\n";
        } else if (weightDifference >= 60) {
            explanation += "The weight gap is very large: " + winner.name + " is about "
                    + String.format("%.0f", weightDifference)
                    + " lbs heavier, giving them a major physical advantage.\n";
        } else if (weightDifference >= 20) {
            explanation += "The weight gap is meaningful: " + winner.name + " is about "
                    + String.format("%.0f", weightDifference)
                    + " lbs heavier, which strongly helps their physical score.\n";
        } else if (weightDifference >= 5) {
            explanation += winner.name + " has a small weight advantage of about "
                    + String.format("%.0f", weightDifference)
                    + " lbs, but it is not treated as the main reason for the prediction.\n";
        } else if (weightDifference <= -20) {
            explanation += winner.name + " is lighter by about "
                    + String.format("%.0f", Math.abs(weightDifference))
                    + " lbs, but their other statistical advantages overcome the size disadvantage.\n";
        } else {
            explanation += "Weight is not the main deciding factor because the difference is small.\n";
        }

        explanation += "Overall, " + winner.name
                + " has the stronger weighted matchup across striking, grappling, defense, experience, physical traits, and style.\n";

        return explanation;
    }

    private static String getAdvantages(Fighter fighter, Fighter opponent) {
        ArrayList<String> advantages = new ArrayList<>();

        if (fighter.slpm >= 5.0) {
            advantages.add("high striking output");
        }

        if (fighter.strAcc >= 45) {
            advantages.add("accurate striking");
        }

        if (fighter.strDef >= 60) {
            advantages.add("strong striking defense");
        }

        if (fighter.tdAvg >= 3.0) {
            advantages.add("strong takedown threat");
        }

        if (fighter.tdAcc >= 50) {
            advantages.add("efficient takedown accuracy");
        }

        if (fighter.tdDef >= 70) {
            advantages.add("strong takedown defense");
        }

        if (fighter.subAvg >= 1.5) {
            advantages.add("dangerous submission threat");
        }

        if (fighter.getWinRate() >= 70) {
            advantages.add("high win rate");
        }

        if (extractNumber(fighter.reach) - extractNumber(opponent.reach) >= 3) {
            advantages.add("reach advantage");
        }

        if (extractNumber(fighter.weight) - extractNumber(opponent.weight) >= 10) {
            advantages.add("major weight advantage");
        }

        if (fighter.tdAvg >= 3.0 && opponent.tdDef < 60) {
            advantages.add("can exploit the opponent's weaker takedown defense");
        }

        if (fighter.slpm >= 5.0 && opponent.strDef < 55) {
            advantages.add("can exploit the opponent's weaker striking defense");
        }

        if (fighter.subAvg >= 1.5 && opponent.tdDef < 60) {
            advantages.add("can create submission opportunities through grappling");
        }

        if (advantages.isEmpty()) {
            return "no major standout advantage, but they are statistically competitive";
        }

        return String.join(", ", advantages);
    }

    private static String getDisadvantages(Fighter fighter, Fighter opponent) {
        ArrayList<String> disadvantages = new ArrayList<>();

        if (fighter.sapm >= 4.5) {
            disadvantages.add("absorbs a high number of significant strikes");
        }

        if (fighter.strDef < 55) {
            disadvantages.add("below-average striking defense");
        }

        if (fighter.tdDef < 60) {
            disadvantages.add("vulnerable takedown defense");
        }

        if (fighter.tdAvg < 1.0 && fighter.subAvg < 0.5) {
            disadvantages.add("limited offensive grappling threat");
        }

        if (fighter.slpm < 3.0) {
            disadvantages.add("low striking output");
        }

        if (fighter.strAcc < 40) {
            disadvantages.add("lower striking accuracy");
        }

        if (fighter.getWinRate() < 50) {
            disadvantages.add("losing record or lower win rate");
        }

        if (extractNumber(opponent.reach) - extractNumber(fighter.reach) >= 3) {
            disadvantages.add("reach disadvantage");
        }

        if (extractNumber(opponent.weight) - extractNumber(fighter.weight) >= 10) {
            disadvantages.add("major weight disadvantage");
        }

        if (opponent.tdAvg >= 3.0 && fighter.tdDef < 60) {
            disadvantages.add("may struggle against the opponent's wrestling");
        }

        if (opponent.slpm >= 5.0 && fighter.strDef < 55) {
            disadvantages.add("may struggle against the opponent's striking volume");
        }

        if (opponent.subAvg >= 1.5 && fighter.tdDef < 60) {
            disadvantages.add("may be exposed to submission threats after takedowns");
        }

        if (disadvantages.isEmpty()) {
            return "no major statistical weakness";
        }

        return String.join(", ", disadvantages);
    }

    private static String determineArchetype(Fighter f) {
        if (f.slpm >= 6.0 && f.strDef >= 60 && f.tdDef >= 80) {
            return "Pressure striker";
        }

        if (f.tdAvg >= 3.0 && f.subAvg >= 2 && f.tdAcc >= 50) {
            return "Hugger / pressure grappler";
        }

        if (f.slpm >= 5.0 && f.sapm >= 4.5 && f.tdDef >= 70) {
            return "Brawler";
        }

        if (f.sapm < 3.0 && f.strAcc >= 50 && f.strDef >= 55 && f.tdDef >= 65 && f.tdAvg <= 1) {
            return "Counter striker";
        }

        if (f.subAvg >= 2.0 && f.tdDef < 65) {
            return "Submission artist";
        }

        if (f.slpm >= 3.5 && f.tdAvg >= 3.0) {
            return "Ground-n-pounder";
        }

        if (f.strAcc>= 50 && f.tdAcc >= 40 && f.strDef >= 60 && f.tdDef >= 80) {
            return "Virtuoso";
        }

        if (f.tdAcc <= 30 && f.slpm < f.sapm && f.tdDef <= 40) {
            return "Punching bag";
        }

        return "Balanced fighter";
    }

    private static void printFighterComparison(Fighter f1, Fighter f2) {
        System.out.printf("%-20s %-20s %-20s\n", "Stat", f1.name, f2.name);
        System.out.println("------------------------------------------------------------");
        System.out.printf("%-20s %-20d %-20d\n", "Wins", f1.wins, f2.wins);
        System.out.printf("%-20s %-20d %-20d\n", "Losses", f1.losses, f2.losses);
        System.out.printf("%-20s %-20s %-20s\n", "Height", f1.height, f2.height);
        System.out.printf("%-20s %-20s %-20s\n", "Weight", f1.weight, f2.weight);
        System.out.printf("%-20s %-20s %-20s\n", "Reach", f1.reach, f2.reach);
        System.out.printf("%-20s %-20s %-20s\n", "Stance", f1.stance, f2.stance);
        System.out.printf("%-20s %-20.2f %-20.2f\n", "SLpM", f1.slpm, f2.slpm);
        System.out.printf("%-20s %-20.2f %-20.2f\n", "Str Acc", f1.strAcc, f2.strAcc);
        System.out.printf("%-20s %-20.2f %-20.2f\n", "SApM", f1.sapm, f2.sapm);
        System.out.printf("%-20s %-20.2f %-20.2f\n", "Str Def", f1.strDef, f2.strDef);
        System.out.printf("%-20s %-20.2f %-20.2f\n", "TD Avg", f1.tdAvg, f2.tdAvg);
        System.out.printf("%-20s %-20.2f %-20.2f\n", "TD Acc", f1.tdAcc, f2.tdAcc);
        System.out.printf("%-20s %-20.2f %-20.2f\n", "TD Def", f1.tdDef, f2.tdDef);
        System.out.printf("%-20s %-20.2f %-20.2f\n", "Sub Avg", f1.subAvg, f2.subAvg);
    }

    private static void runPreparedSQLAndPrint(String query, ArrayList<Object> params, Connection conn) {
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {

            for (int i = 0; i < params.size(); i++) {
                Object param = params.get(i);

                if (param instanceof Integer) {
                    pstmt.setInt(i + 1, (Integer) param);
                } else if (param instanceof Double) {
                    pstmt.setDouble(i + 1, (Double) param);
                } else {
                    pstmt.setString(i + 1, param.toString());
                }
            }

            ResultSet rs = pstmt.executeQuery();
            printResultSet(rs);

        } catch (SQLException e) {
            System.out.println("SQL error: " + e.getMessage());
        }
    }

    private static void printResultSet(ResultSet rs) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        int cols = metaData.getColumnCount();

        ArrayList<String[]> rows = new ArrayList<>();
        int[] colWidths = new int[cols];

        for (int i = 0; i < cols; i++) {
            colWidths[i] = metaData.getColumnLabel(i + 1).length();
        }

        while (rs.next()) {
            String[] row = new String[cols];

            for (int i = 0; i < cols; i++) {
                String value = rs.getString(i + 1);

                if (value == null) {
                    value = "";
                }

                row[i] = value;

                if (value.length() > colWidths[i]) {
                    colWidths[i] = value.length();
                }
            }

            rows.add(row);
        }

        for (int i = 1; i <= cols; i++) {
            System.out.printf("%-" + (colWidths[i - 1] + 2) + "s", metaData.getColumnLabel(i));
        }

        System.out.println();

        for (int width : colWidths) {
            System.out.print("-".repeat(width + 2));
        }

        System.out.println();

        for (String[] row : rows) {
            for (int i = 0; i < cols; i++) {
                System.out.printf("%-" + (colWidths[i] + 2) + "s", row[i]);
            }
            System.out.println();
        }

        if (rows.isEmpty()) {
            System.out.println("No results found.");
        }
    }

    private static double parsePercent(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }

        value = value.replace("%", "").trim();

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double extractNumber(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }

        String cleaned = value.replaceAll("[^0-9.]", "");

        if (cleaned.isBlank()) {
            return 0;
        }

        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static class Fighter {
        String name;
        int wins;
        int losses;
        String height;
        String weight;
        String reach;
        String stance;
        String dob;

        double slpm;
        double strAcc;
        double sapm;
        double strDef;
        double tdAvg;
        double tdAcc;
        double tdDef;
        double subAvg;

        Fighter(String name, int wins, int losses, String height, String weight, String reach,
                String stance, String dob, double slpm, double strAcc, double sapm,
                double strDef, double tdAvg, double tdAcc, double tdDef, double subAvg) {

            this.name = name;
            this.wins = wins;
            this.losses = losses;
            this.height = height;
            this.weight = weight;
            this.reach = reach;
            this.stance = stance;
            this.dob = dob;
            this.slpm = slpm;
            this.strAcc = strAcc;
            this.sapm = sapm;
            this.strDef = strDef;
            this.tdAvg = tdAvg;
            this.tdAcc = tdAcc;
            this.tdDef = tdDef;
            this.subAvg = subAvg;
        }

        double getWinRate() {
            int total = wins + losses;

            if (total == 0) {
                return 50;
            }

            return ((double) wins / total) * 100;
        }
    }
}
