import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class login extends JFrame implements ActionListener {
    JButton login,signup,clear;
    JTextField cardTextField ;
    JPasswordField pinTextField;

    login() {

        setTitle("AUTOMATIC TALLER MACHINE");
        setLayout(null);
        ImageIcon i1 = new ImageIcon(ClassLoader.getSystemResource("bank logo.png"));
        Image i2 = i1.getImage().getScaledInstance(100, 100, Image.SCALE_DEFAULT);
        ImageIcon i3 = new ImageIcon(i2);
        JLabel label = new JLabel(i3);
        label.setBounds(20, 10, 130, 120);
        add(label);

        JLabel text = new JLabel("WELCOME TO SBI KATARA HILLS BRANCH");
        text.setBounds(200, 40, 1000, 40);
        text.setFont(new Font("osward", Font.BOLD, 38));

        JLabel cardno = new JLabel("CARD NO:");
        cardno.setBounds(120, 150, 150, 40);
        cardno.setFont(new Font("Raieway", Font.BOLD, 28));
        add(cardno);

        cardTextField = new JTextField();
        cardTextField.setBounds(300, 150, 230, 40);
        cardTextField.setFont(new Font("Arial",Font.BOLD,14));
        add(cardTextField);

        JLabel pin = new JLabel("PIN:");
        pin.setBounds(120, 220, 1000, 40);
        pin.setFont(new Font("Raieway", Font.BOLD, 28));
        add(pin);
        pinTextField = new  JPasswordField();
        pinTextField.setBounds(300, 220, 230, 40);
        pinTextField.setFont(new Font("Arial",Font.BOLD,14));
        add( pinTextField);


        login = new JButton("SIGN IN");
        login.setBounds(300, 300, 100, 30);
        login.setBackground(Color.black);
        login.setForeground(Color.white);
        login.addActionListener(this);

        add(login);

        clear = new JButton("CLEAR");
        clear.setBounds(430, 300, 100, 30);
        clear.setBackground(Color.black);
        clear.setForeground(Color.white);
        clear.addActionListener(this);

        add(clear);

        signup = new JButton("SIGN UP");
        signup.setBounds(300, 350, 230, 30);
        signup.setBackground(Color.black);
        signup.setForeground(Color.white);
        signup.addActionListener(this);

        add(signup);


        getContentPane().setBackground(Color.BLACK);
        add(text);

        setSize(800, 400);
        setVisible(true);
        setLocation(350, 200);

    }
    public void actionPerformed(ActionEvent ae){
        if (ae.getSource()== clear){
            cardTextField.setText("");
            pinTextField.setText("");
            
        } else if (ae.getSource()==login) {
            
        } else if (ae.getSource()==signup) {
            setVisible(false);
            new SIGNUP1().setVisible(true);
            
        }


    }
    public static void main(String[] args) {
        new login();

    }
}
