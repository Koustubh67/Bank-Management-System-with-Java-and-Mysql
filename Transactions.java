import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Date;

public class Transactions extends JFrame implements ActionListener {
    JButton deposit, withdraw, balance, exit;
    String cardnumber;

    Transactions(String cardnumber) {
        this.cardnumber = cardnumber;
        setTitle("TRANSACTIONS");
        setLayout(null);

        JLabel text = new JLabel("PLEASE SELECT YOUR TRANSACTION");
        text.setFont(new Font("Raleway", Font.BOLD, 24));
        text.setBounds(110, 40, 500, 40);
        add(text);

        deposit = makeButton("DEPOSIT", 110, 120);
        withdraw = makeButton("WITHDRAW", 360, 120);
        balance = makeButton("BALANCE ENQUIRY", 110, 190);
        exit = makeButton("EXIT", 360, 190);

        getContentPane().setBackground(Color.WHITE);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(700, 330);
        setLocation(350, 200);
        setVisible(true);
    }

    private JButton makeButton(String label, int x, int y) {
        JButton b = new JButton(label);
        b.setBounds(x, y, 220, 40);
        b.setBackground(Color.black);
        b.setForeground(Color.white);
        b.setFont(new Font("Raleway", Font.BOLD, 14));
        b.addActionListener(this);
        add(b);
        return b;
    }

    private int getBalance(Conn c) throws Exception {
        PreparedStatement ps = c.C.prepareStatement("select type, amount from bank where cardnumber = ?");
        ps.setString(1, cardnumber);
        ResultSet rs = ps.executeQuery();
        int total = 0;
        while (rs.next()) {
            int amount = Integer.parseInt(rs.getString("amount"));
            total += rs.getString("type").equals("DEPOSIT") ? amount : -amount;
        }
        return total;
    }

    private Integer askAmount(String type) {
        String input = JOptionPane.showInputDialog(this, "ENTER AMOUNT TO " + type);
        if (input == null) return null;
        try {
            int amount = Integer.parseInt(input.trim());
            if (amount > 0) return amount;
        } catch (NumberFormatException ignored) {
        }
        JOptionPane.showMessageDialog(this, "PLEASE ENTER A VALID AMOUNT");
        return null;
    }

    private void record(Conn c, String type, int amount) throws Exception {
        PreparedStatement ps = c.C.prepareStatement("insert into bank values(?,?,?,?)");
        ps.setString(1, cardnumber);
        ps.setString(2, new Date().toString());
        ps.setString(3, type);
        ps.setString(4, "" + amount);
        ps.executeUpdate();
    }

    public void actionPerformed(ActionEvent ae) {
        if (ae.getSource() == exit) {
            setVisible(false);
            new login().setVisible(true);
            return;
        }
        try {
            Conn c = new Conn();
            if (ae.getSource() == deposit) {
                Integer amount = askAmount("DEPOSIT");
                if (amount == null) return;
                record(c, "DEPOSIT", amount);
                JOptionPane.showMessageDialog(this, "RS " + amount + " DEPOSITED SUCCESSFULLY");
            } else if (ae.getSource() == withdraw) {
                Integer amount = askAmount("WITHDRAW");
                if (amount == null) return;
                if (getBalance(c) < amount) {
                    JOptionPane.showMessageDialog(this, "INSUFFICIENT BALANCE");
                    return;
                }
                record(c, "WITHDRAW", amount);
                JOptionPane.showMessageDialog(this, "RS " + amount + " WITHDRAWN SUCCESSFULLY");
            } else if (ae.getSource() == balance) {
                JOptionPane.showMessageDialog(this, "YOUR CURRENT BALANCE IS RS " + getBalance(c));
            }
        } catch (Exception e) {
            System.out.println(e);
            JOptionPane.showMessageDialog(this, "DATABASE ERROR: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        new Transactions("");
    }
}
