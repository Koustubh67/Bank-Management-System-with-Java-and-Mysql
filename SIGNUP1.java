import com.sun.tools.jconsole.JConsoleContext;
import com.sun.tools.jconsole.JConsolePlugin;

import javax.swing.*;
import java.awt.*;
import java.io.Console;
import java.util.* ;
import com.toedter.calendar.JDateChooser;
import java.awt.event.*;



public class SIGNUP1 extends JFrame implements ActionListener{
    long random;
    JTextField nameTextFiled , fnameTextFiled,dobTextFiled, emailTextFiled, addressTextFiled,cityTextFiled,stateTextFiled,pinTextFiled, countryTextFiled;
    JButton next;
    JRadioButton male,female,other,married,unmarried,student;
    JDateChooser dateChooser;
    SIGNUP1(){
        setLayout(null);
        Random ran= new Random();
         random= (Math.abs(ran.nextLong()%9000l)+1000l);
        JLabel formno=new JLabel("APPLICCATION FORM NO."+ random);
        formno.setFont(new Font("Raieway",Font.BOLD,38));
        formno.setBounds(140,20,600,40);
        add(formno);


        JLabel presonalDetails=new JLabel("PAGE-1:  PRESONAL DETAILS");
        presonalDetails.setFont(new Font("Raieway",Font.BOLD,26));
        presonalDetails.setBounds(225,60,400,70);
        add(presonalDetails);
        JLabel name =new JLabel(" NAME:");
        name.setFont(new Font("Raieway",Font.BOLD,20));
        name.setBounds(100,140,115,30);
        add(name);
        nameTextFiled = new JTextField();
        nameTextFiled.setFont(new Font("Rainway",Font.BOLD,14));
        nameTextFiled.setBounds(300,140,400,30);
        add(nameTextFiled);
        JLabel fname =new JLabel("FATHER'S NAME:");
        fname.setFont(new Font("Raieway",Font.BOLD,20));
        fname.setBounds(100,190,200,30);
        add(fname);
         fnameTextFiled = new JTextField();
        fnameTextFiled.setFont(new Font("Rainway",Font.BOLD,14));
        fnameTextFiled.setBounds(300,190,400,30);
        add(fnameTextFiled);
        JLabel dob =new JLabel("DATE OF BIRTH:");
        dob.setFont(new Font("Raieway",Font.BOLD,20));
        dob.setBounds(100,240,200,30);
        add(dob);
         dateChooser = new JDateChooser();
        dateChooser.setBounds(300,240,400,30);
        dateChooser.setForeground(new Color(105,105,105));
        add(dateChooser);
        JLabel gender =new JLabel("GENDER:");
        gender.setFont(new Font("Raieway",Font.BOLD,20));
        gender.setBounds(100,290,200,30);
        add(gender);
         male =new JRadioButton("MALE");
        male.setBounds(300,290,60,30);
        male.setBackground(Color.white);
        add(male);
         female =new JRadioButton("FEMALE");
        female.setBounds(450,290,120,30);
        female.setBackground(Color.PINK);
        add(female);
        ButtonGroup gendergroup = new ButtonGroup();
        gendergroup.add(male);
        gendergroup.add(female);
        JLabel email =new JLabel("EMAIL ADDRESS:");
        email.setFont(new Font("Raieway",Font.BOLD,20));
        email.setBounds(100,340,200,30);
        add(email);
         emailTextFiled = new JTextField();
        emailTextFiled.setFont(new Font("Rainway",Font.BOLD,14));
        emailTextFiled.setBounds(300,340,400,30);
        add(emailTextFiled);
        JLabel marital =new JLabel("MARITAL STATUS :");
        marital.setFont(new Font("Raieway",Font.BOLD,20));
        marital.setBounds(100,390,200,30);
        add(marital);
         married =new JRadioButton("MARRIED");
        married.setBounds(300,390,100,30);
        married.setBackground(Color.pink);
        add(married);
         unmarried =new JRadioButton("UNMRRIED");
        unmarried.setBounds(410,390,100,30);
        unmarried.setBackground(Color.PINK);
        add(unmarried);
         student =new JRadioButton("STUDENT");
        student.setBounds(520,390,100,30);
        student.setBackground(Color.PINK);
        add(student);
         other =new JRadioButton("OTHER");
        other.setBounds(630,390,100,30);
        other.setBackground(Color.PINK);
        add(other);
        ButtonGroup maritalgroup = new ButtonGroup();
        maritalgroup.add(married);
        maritalgroup.add(unmarried);
        maritalgroup.add(student);
        maritalgroup.add(other);
        JLabel address =new JLabel("ADDRESS :");
        address.setFont(new Font("Raieway",Font.BOLD,20));
        address.setBounds(100,440,200,30);
        add(address);
        addressTextFiled = new JTextField();
        addressTextFiled.setFont(new Font("Rainway",Font.BOLD,14));
        addressTextFiled.setBounds(300,440,400,30);
        add(addressTextFiled);
        JLabel city =new JLabel("CITY :");
        city.setFont(new Font("Raieway",Font.BOLD,20));
        city.setBounds(100,490,200,30);
        add(city);
         cityTextFiled = new JTextField();
        cityTextFiled.setFont(new Font("Rainway",Font.BOLD,14));
        cityTextFiled.setBounds(300,490,400,30);
        add(cityTextFiled);
        JLabel state =new JLabel("STATE :");
        state.setFont(new Font("Raieway",Font.BOLD,20));
        state.setBounds(100,540,200,30);
        add(state);
         stateTextFiled = new JTextField();
        stateTextFiled.setFont(new Font("Rainway",Font.BOLD,14));
        stateTextFiled.setBounds(300,540,400,30);
        add(stateTextFiled);
        JLabel pin =new JLabel("PIN CODE :");
        pin.setFont(new Font("Raieway",Font.BOLD,20));
        pin.setBounds(100,590,200,30);
        add(pin);
         pinTextFiled = new JTextField();
        pinTextFiled.setFont(new Font("Rainway",Font.BOLD,14));
        pinTextFiled.setBounds(300,590,400,30);
        add(pinTextFiled);


        JLabel country =new JLabel("COUNTRY :");
        country.setFont(new Font("Raieway",Font.BOLD,20));
        country.setBounds(100,630,200,30);
        add(country);
        countryTextFiled = new JTextField();
        countryTextFiled.setFont(new Font("Rainway",Font.BOLD,14));
        countryTextFiled.setBounds(300,630,400,30);
        add(countryTextFiled);

        next = new JButton("NEXT");
        next.setBackground(Color.black);
        next.setForeground(Color.gray);
        next.setFont(new Font("Raleway",Font.BOLD,14));
        next.setBounds(950,600,80,30);
        next.addActionListener(this);
        add(next);











        getContentPane().setBackground(Color.black);
        setSize(850,800);
        setLocation(350,10);
        setVisible(true);


    }

    public void actionPerformed(ActionEvent ae){
        String formno= "", random;
        String name= nameTextFiled.getText();
        String fname = fnameTextFiled.getText();
        String dob = ((JTextField)dateChooser.getDateEditor().getUiComponent()).getText();
        String gender = null;



        if (male.isSelected()){
            gender = "male";
        } else if (female.isSelected()) {
            gender = "female";

        }
        String email = emailTextFiled.getText();
        String marital = null;
        if (married.isSelected()){
            marital="married";
        } else if (unmarried.isSelected()) {
            marital = "unmarried";


        } else if (other.isSelected()) {
            marital ="other";


        } else if (student.isSelected()) {
            marital="student";

        }
        String city = cityTextFiled.getText();
        String address = addressTextFiled.getText();
        String country = countryTextFiled.getText();
        String state = stateTextFiled.getText();
        String pin = pinTextFiled.getText();


        try {
            if (name.equals("")){
                JOptionPane.showMessageDialog(null,"NMAE IS REQUIRED FOR NEXT PROCESS");
            }else {
                Conn C = new Conn();
                String query = "insert into signup value('" + formno + "','" + name + "','" + fname + "','" + dob + "','" + gender + "','" + email + "','" + marital + "','" + address + "','" + city + "','" + state+ "','" + pin + "','" + country + "')";
                 C.S.executeUpdate(query);

                 setVisible(false);
                 new SignupTwo(formno).setVisible(true);
            }
            if (fname.equals("")){
                JOptionPane.showMessageDialog(null,"FATHER NAME IS REQUIRED FOR NEXT PROCESS");
            }
            if (dob.equals("")){
                JOptionPane.showMessageDialog(null,"DOB IS REQUIRED FOR NEXT PROCESS");
            }
            if (email.equals("")){
                JOptionPane.showMessageDialog(null,"EMAIL IS REQUIRED FOR NEXT PROCESS");

            }


            if (city.equals("")){
                JOptionPane.showMessageDialog(null,"CITY NAME IS REQUIRED FOR NEXT PROCESS");
            }
            if (state.equals("")){
                JOptionPane.showMessageDialog(null,"STATE NAME IS REQUIRED FOR NEXT PROCESS");

            }
            if (address.equals("")){
                JOptionPane.showMessageDialog(null,"ADDRESS IS REQUIRED FOR NEXT PROCESS");



            }
            if (country.equals("")){
                JOptionPane.showMessageDialog(null,"COUNTRY NAME IS REQUIRED FOR NEXT PROCESS");

            }
            if (pin.equals("")){
                JOptionPane.showMessageDialog(null,"PIN IS REQUIRED FOR NEXT PROCESS");
            }


        }catch (Exception e){
            System.out.println(e);
        }

    }

    public static void main(String args[]){

        new SIGNUP1();
    }
}
