import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.PreparedStatement;
import java.util.Random;

public class SignupThree extends JFrame implements ActionListener{
    JRadioButton r1, r2 ,r3,r4;
    JCheckBox c1, c2, c3, c4, c5, c6, c7;
    JButton submit, cancle;
    String formno;
    SignupThree(String formno){
        this.formno = formno;
        setLayout(null);
        JLabel L1= new JLabel("PAGE-3:ACCOUNT DETAILS");
        L1.setFont(new Font("Raleway",Font.BOLD,22));
        L1.setForeground(Color.WHITE);
        L1.setBounds(280,40,400,40);
        add(L1);
        JLabel type = new JLabel("ACCOUNT TYPE:-");
        type.setFont(new Font("Raleway",Font.BOLD,22));
        type.setForeground(Color.WHITE);
        type.setBounds(100,140,200,30);
        add(type);

        r1= new JRadioButton("SAVING ACCOUNT");
        r1.setFont(new Font("Raleway", Font.BOLD,16));
        r1.setBackground(Color.white);
        r1.setBounds(100,180,170,20);
        add(r1);

        r2= new JRadioButton("FIXDE DEPOSITE ACCOUNT");
        r2.setFont(new Font("Raleway", Font.BOLD,16));
        r2.setBackground(Color.white);
        r2.setBounds(350,180,250,20);
        add(r2);

        r3= new JRadioButton("CURRENT ACCOUNT");
        r3.setFont(new Font("Raleway", Font.BOLD,16));
        r3.setBackground(Color.white);
        r3.setBounds(100,220,200,20);
        add(r3);

        r4= new JRadioButton("RECURRING DEPOSITE ACCOUNT");
        r4.setFont(new Font("Raleway", Font.BOLD,16));
        r4.setBackground(Color.white);
        r4.setBounds(350,220,300,20);
        add(r4);

        ButtonGroup groupaccount = new ButtonGroup();
        groupaccount.add(r1);
        groupaccount.add(r2);
        groupaccount.add(r3);
        groupaccount.add(r4);

        JLabel card = new JLabel("CARD NUMBER:-");
        card.setFont(new Font("Raleway",Font.BOLD,22));
        card.setForeground(Color.WHITE);
        card.setBounds(100,300,200,30);
        add(card);

        JLabel number = new JLabel("XXXX-XXXX-XXXX-4184");
        number.setFont(new Font("Raleway",Font.BOLD,22));
        number.setForeground(Color.WHITE);
        number.setBounds(330,300,300,30);
        add(number);

        JLabel carddetail = new JLabel("YOUR 16 DIGIT CARD NUMBER");
        carddetail.setFont(new Font("Raleway",Font.BOLD,12));
        carddetail.setForeground(Color.WHITE);
        carddetail.setBounds(100,330,300,20);
        add(carddetail);

        JLabel pin = new JLabel("PIN:-");
        pin.setFont(new Font("Raleway",Font.BOLD,22));
        pin.setForeground(Color.WHITE);
        pin.setBounds(100,370,200,30);
        add(pin);
        JLabel pindetail = new JLabel("YOUR 4 DIGIT PASSWORD");
        pindetail.setFont(new Font("Raleway",Font.BOLD,12));
        pindetail.setForeground(Color.WHITE);
        pindetail.setBounds(100,400,300,20);
        add(pindetail);

        JLabel pnumber = new JLabel("XXXX");
        pnumber.setFont(new Font("Raleway",Font.BOLD,22));
        pnumber.setForeground(Color.WHITE);
        pnumber.setBounds(330,370,300,30);
        add(pnumber);

        JLabel services = new JLabel("SERVICES REQUIRED:-");
        services.setFont(new Font("Raleway",Font.BOLD,22));
        services.setForeground(Color.WHITE);
        services.setBounds(100,450,300,30);
        add(services);

        c1 = new JCheckBox("ATM CARD");
        c1.setBackground(Color.WHITE);
        c1.setFont(new Font("Raleway",Font.BOLD,16));
        c1.setBounds(100,500,120,30);
        add(c1);
        c2 = new JCheckBox("INTERNET BANKING");
        c2.setBackground(Color.WHITE);
        c2.setFont(new Font("Raleway",Font.BOLD,16));
        c2.setBounds(350,500,200,30);
        add(c2);
        c3 = new JCheckBox("MOBILE BANKING");
        c3.setBackground(Color.WHITE);
        c3.setFont(new Font("Raleway",Font.BOLD,16));
        c3.setBounds(100,550,200,30);
        add(c3);
        c4 = new JCheckBox("EMAIL & SMS ALERT");
        c4.setBackground(Color.WHITE);
        c4.setFont(new Font("Raleway",Font.BOLD,16));
        c4.setBounds(350,550,200,30);
        add(c4);
        c5 = new JCheckBox("CHEQUE BOOK");
        c5.setBackground(Color.WHITE);
        c5.setFont(new Font("Raleway",Font.BOLD,16));
        c5.setBounds(100,600,200,30);
        add(c5);
        c6 = new JCheckBox("E-STATEMENT");
        c6.setBackground(Color.WHITE);
        c6.setFont(new Font("Raleway",Font.BOLD,16));
        c6.setBounds(350,600,200,30);
        add(c6);
        c7 = new JCheckBox("HEREBY DECLARES THAT ABOVE ENTRE DETAILE ARE CORRECT TO THE BEST OF MY KNOWLEDGE ");
        c7.setBackground(Color.WHITE);
        c7.setFont(new Font("Raleway",Font.BOLD,12));
        c7.setBounds(100,650,800,30);
        add(c7);

        submit= new JButton("SUBMIT");
        submit.setBackground(Color.WHITE);
        submit.setForeground(Color.black);
        submit.setFont(new Font("Raleway",Font.BOLD,14));
        submit.setBounds(100,700,100,30);
        submit.addActionListener(this);
        add(submit);



        cancle= new JButton("CANCEL");
        cancle.setBackground(Color.WHITE);
        cancle.setForeground(Color.black);
        cancle.setFont(new Font("Raleway",Font.BOLD,14));
        cancle.setBounds(240,700,100,30);
        cancle.addActionListener(this);
        add(cancle);

        getContentPane().setBackground(Color.black);







        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(900, 800);
        setLocation(350, 0);
        setVisible(true);
    }

    public void  actionPerformed(ActionEvent ae){
        if(ae.getSource()== submit){
            String accountType = null;
            if (r1.isSelected()){
                accountType="SAVING ACCOUNT";
            } else if (r2.isSelected()) {
                accountType="FIXED DEPOSITE ACCOUNT";
            } else if (r3.isSelected()) {
                accountType="CURRENT ACCOUNT";
            } else if (r4.isSelected()) {
                accountType="RECURRING DEPOSITE ACCOUNT";
            }
            if (accountType == null){
                JOptionPane.showMessageDialog(null,"ACCOUNT TYPE IS REQUIRED");
                return;
            }
            if (!c7.isSelected()){
                JOptionPane.showMessageDialog(null,"PLEASE ACCEPT THE DECLARATION");
                return;
            }

            Random random = new Random();
            String cardnummber = ""+ Math.abs((random.nextLong() % 9000000L) + 5040936000000000L);
            String pinnumber = ""+ Math.abs((random.nextLong() % 9000L)+1000L);

            // Every selected service is saved, not just the first one.
            String facility ="";
            JCheckBox[] services = {c1, c2, c3, c4, c5, c6};
            for (JCheckBox c : services) {
                if (c.isSelected()) {
                    facility = facility + " " + c.getText();
                }
            }
            facility = facility.trim();

            try {
                Conn conn = new Conn();
                PreparedStatement ps1 = conn.C.prepareStatement("insert into signupthree values(?,?,?,?,?)");
                ps1.setString(1, formno);
                ps1.setString(2, accountType);
                ps1.setString(3, cardnummber);
                ps1.setString(4, pinnumber);
                ps1.setString(5, facility);
                ps1.executeUpdate();

                PreparedStatement ps2 = conn.C.prepareStatement("insert into login values(?,?,?)");
                ps2.setString(1, formno);
                ps2.setString(2, cardnummber);
                ps2.setString(3, pinnumber);
                ps2.executeUpdate();

                JOptionPane.showMessageDialog(null,"ACCOUNT CREATED\nCARD NUMBER: " + cardnummber + "\nPIN: " + pinnumber + "\n\nPLEASE NOTE THESE DOWN TO SIGN IN");

                setVisible(false);
                new login().setVisible(true);
            }catch (Exception e){
                System.out.println(e);
                JOptionPane.showMessageDialog(null, "DATABASE ERROR: " + e.getMessage());
            }

        } else if (ae.getSource()== cancle) {
            setVisible(false);
            new login().setVisible(true);
        }
    }

    public static void main(String[] args) {
        new SignupThree("");

    }
}
