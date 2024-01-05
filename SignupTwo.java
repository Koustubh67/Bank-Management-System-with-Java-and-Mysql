import com.toedter.calendar.JDateChooser;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.*;


public class SignupTwo extends JFrame implements ActionListener {

    long random;
    JTextField pan, aadhar;
    JButton next;
    JRadioButton syes, sno, eyes, eno;
    JComboBox religion, category, occupation, qualification, income;
    String formno;

    SignupTwo(String formno) {

        this.formno = formno;


        setTitle("NEW ACCOUNT APPLICATION FORM - PAGE 2");
        setLayout(null);


        JLabel additionalDetails = new JLabel("PAGE-2:  ADDITIONAL DETAILS");
        additionalDetails.setFont(new Font("Raieway", Font.BOLD, 26));
        additionalDetails.setBounds(140, 20, 600, 40);
        add(additionalDetails);


        JLabel name = new JLabel("RELIGION:");
        name.setFont(new Font("Raieway", Font.BOLD, 20));
        name.setBounds(100, 140, 115, 30);
        add(name);
        String valReligion[] = {"HINDU", "MUSLIM", "SIKH", "CHRISTIAN", "JANI"};
        religion = new JComboBox(valReligion);
        religion.setBounds(300, 140, 400, 30);
        religion.setBackground(Color.WHITE);
        add(religion);


        JLabel fname = new JLabel("CATEGORY:");
        fname.setFont(new Font("Raieway", Font.BOLD, 20));
        fname.setBounds(100, 190, 200, 30);
        add(fname);
        String valCategory[] = {"GENRAL", "OBC", "ST", "SC", "OTHERS"};
        category = new JComboBox(valCategory);
        category.setBounds(300, 190, 400, 30);
        category.setBackground(Color.WHITE);
        add(category);


        JLabel date = new JLabel("INCOME:");
        date.setFont(new Font("Raieway", Font.BOLD, 20));
        date.setBounds(100, 240, 200, 30);
        add(date);
        String valIncome[] = {"NULL", "<1,50,000", "<2,50,000", "<5,00,000", "UPTO 10,00,000", "UNDER 1,000,00,00", "UPTO 5,000,00,00", "UPTO 10,000,00,00"};
        income = new JComboBox(valIncome);
        income.setBounds(300, 240, 400, 30);
        income.setBackground(Color.WHITE);
        add(income);


        JLabel educational = new JLabel("EDUCATIONAL");
        educational.setFont(new Font("Raieway", Font.BOLD, 20));
        educational.setBounds(100, 290, 200, 30);
        add(educational);

        JLabel email = new JLabel("QUALIFICATION:");
        email.setFont(new Font("Raieway", Font.BOLD, 20));
        email.setBounds(100, 315, 200, 30);
        add(email);

        String valQualification[] = {"10TH PASS", "12TH PASS", "NON GRADUATE", "GRADUATE", "POST GRADUATE", "DOCTARATE", "NEVER WENT SCHOOL", "OTHER"};
        qualification = new JComboBox(valQualification);
        qualification.setBounds(300, 310, 400, 30);
        qualification.setBackground(Color.WHITE);
        add(qualification);

        JLabel marital = new JLabel("OCCUPATION :");
        marital.setFont(new Font("Raieway", Font.BOLD, 20));
        marital.setBounds(100, 390, 200, 30);
        add(marital);
        String valOccupation[] = {"SALARIED", "SELF EMPLOYED", "BUSSINESS MEN/WOMEN", "STUDENT", "RETIRED", "GOVERMENT EMPLOYE", "DEFENCE", "POLICE", "SERVICE"};
        occupation = new JComboBox(valOccupation);
        occupation.setBounds(300, 390, 400, 30);
        occupation.setBackground(Color.WHITE);
        add(occupation);
        JLabel adderess = new JLabel("PAN NUMBER :");
        adderess.setFont(new Font("Raieway", Font.BOLD, 20));
        adderess.setBounds(100, 440, 200, 30);
        add(adderess);
        pan = new JTextField();
        pan.setFont(new Font("Rainway", Font.BOLD, 14));
        pan.setBounds(300, 440, 400, 30);
        add(pan);
        JLabel city = new JLabel("AADHAR NUMBER :");
        city.setFont(new Font("Raieway", Font.BOLD, 20));
        city.setBounds(100, 490, 200, 30);
        add(city);
        aadhar = new JTextField();
        aadhar.setFont(new Font("Rainway", Font.BOLD, 14));
        aadhar.setBounds(300, 490, 400, 30);
        add(aadhar);
        JLabel senior = new JLabel("SENIOR CITIZEN :");
        senior.setFont(new Font("Raieway", Font.BOLD, 20));
        senior.setBounds(100, 540, 200, 30);
        add(senior);
        syes = new JRadioButton("YES");
        syes.setBounds(300, 540, 100, 30);
        syes.setBackground(Color.white);
        add(syes);
        sno = new JRadioButton("NO");
        sno.setBounds(450, 540, 100, 30);
        sno.setBackground(Color.PINK);
        add(sno);
        ButtonGroup gendergroup = new ButtonGroup();
        gendergroup.add(syes);
        gendergroup.add(sno);

        JLabel exisiting = new JLabel("EXISITING ACCOUNT :");
        exisiting.setFont(new Font("Raieway", Font.BOLD, 20));
        exisiting.setBounds(100, 590, 200, 30);
        add(exisiting);
        eyes = new JRadioButton("YES");
        eyes.setBounds(300, 590, 100, 30);
        eyes.setBackground(Color.white);
        add(eyes);
        eno = new JRadioButton("NO");
        eno.setBounds(450, 590, 100, 30);
        eno.setBackground(Color.PINK);
        add(eno);
        ButtonGroup egendergroup = new ButtonGroup();
        gendergroup.add(eyes);
        gendergroup.add(eno);


        next = new JButton("NEXT");
        next.setBackground(Color.black);
        next.setForeground(Color.gray);
        next.setFont(new Font("Raleway", Font.BOLD, 14));
        next.setBounds(950, 600, 80, 30);
        next.addActionListener(this);
        add(next);


        getContentPane().setBackground(Color.black);
        setSize(850, 800);
        setLocation(350, 10);
        setVisible(true);


    }

    public void actionPerformed(ActionEvent ae) {
        String formno = "", random;
        String sreligion = (String) religion.getSelectedItem();
        String scategory = (String) category.getSelectedItem();
        String sincome = (String) income.getSelectedItem();
        String squalification = (String) qualification.getSelectedItem();
        String soccupation = (String) occupation.getSelectedItem();
        String seniorcitizen = null;


        if (syes.isSelected()) {
            seniorcitizen = "YES";
        } else if (sno.isSelected()) {
            seniorcitizen = "NO";

        }

        String exisitingaccount = null;
        if (eyes.isSelected()) {
            exisitingaccount = "YES";
        } else if (eno.isSelected()) {
            exisitingaccount = "NO";


            String span = pan.getText();
            String saadhar = aadhar.getText();


            try {
                if (religion.equals("")) {
                    JOptionPane.showMessageDialog(null, "RELIGION IS REQUIRED FOR NEXT PROCESS");
                } else {
                    Conn C = new Conn();
                    String query = "insert into signuptwo value('" + formno + "','" + sreligion + "','" + scategory + "','" + sincome + "','" + squalification + "','" + soccupation + "','" + span + "','" + saadhar + "','" + exisitingaccount + "','" + seniorcitizen + "',')";
                    C.S.executeUpdate(query);
                    //SIGNUP3 OBJECT
                    setVisible(false);
                    new SignupThree(formno).setVisible(true);
                }
                if (category.equals("")) {
                    JOptionPane.showMessageDialog(null, "CATEGORY NAME IS REQUIRED FOR NEXT PROCESS");
                }
                if (qualification.equals("")) {
                    JOptionPane.showMessageDialog(null, "QUALIFICAION IS REQUIRED FOR NEXT PROCESS");
                }
                if (income.equals("")) {
                    JOptionPane.showMessageDialog(null, "INCOME IS REQUIRED FOR NEXT PROCESS");

                }


                if (occupation.equals("")) {
                    JOptionPane.showMessageDialog(null, "OCCUPATION NAME IS REQUIRED FOR NEXT PROCESS");
                }
                if (pan.equals("")) {
                    JOptionPane.showMessageDialog(null, "PAN NAME IS REQUIRED FOR NEXT PROCESS");

                }
                if (aadhar.equals("")) {
                    JOptionPane.showMessageDialog(null, "AADHAR IS REQUIRED FOR NEXT PROCESS");


                }
                if (seniorcitizen.equals("")) {
                    JOptionPane.showMessageDialog(null, "SENIORCITIZENS NAME IS REQUIRED FOR NEXT PROCESS");

                }
                if (exisitingaccount.equals("")) {
                    JOptionPane.showMessageDialog(null, "EXISITINGACCOUNT NAME IS REQUIRED FOR NEXT PROCESS");

                }


            } catch (Exception e) {
                System.out.println(e);
            }

        }

    }

public static void main(String args []){

            new SignupTwo("1");
        }

    }


