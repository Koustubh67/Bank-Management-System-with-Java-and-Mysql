import java.sql.*;
public class Conn {
    Connection C;
    Statement S;
    public Conn(){
        try {
            C= DriverManager.getConnection("jdbc:mysql:///bankmanagmentsystem","root","Koustubh@123");
            S=C.createStatement();
        }catch (Exception e){
            System.out.println("e");

        }
    }
}
/*
if (gender.equals("")){
                JOptionPane.showMessageDialog(male>,"GENDER IS REQUIRED FOR NEXT PROCESS");

            }
             if (marital.equals("")){
                JOptionPane.showMessageDialog(null,"MARITAL IS REQURIED FOR NEXT PROCESS");
            }
 */