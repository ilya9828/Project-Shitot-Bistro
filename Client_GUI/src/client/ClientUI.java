// Source code is decompiled from a .class file using FernFlower decompiler (from Intellij IDEA).
package client;

import java.util.HashMap;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ClientUI extends Application {
   public static ClientController chat;

   public ClientUI() {
   }

   public static void main(String[] args) throws Exception {
      launch(args);
   }

   public void start(Stage primaryStage) throws Exception {
      FXMLLoader loader = new FXMLLoader(this.getClass().getResource("/gui/ConnectionSetup.fxml"));
      Parent root = (Parent)loader.load();
      Scene scene = new Scene(root);
      scene.getStylesheets().add(this.getClass().getResource("/gui/ConnectionSetup.css").toExternalForm());
      primaryStage.setScene(scene);
      primaryStage.setTitle("Connection Setup Tool");
      primaryStage.setWidth(700.0);
      primaryStage.setHeight(550.0);
      primaryStage.setMinWidth(650.0);
      primaryStage.setMinHeight(500.0);
      primaryStage.setOnCloseRequest((closeEvent) -> {
         try {
            if (chat != null) {
               System.out.println("Disconnecting from the Server...");
               HashMap<String, String> EndingConnections = new HashMap();
               EndingConnections.put("Disconnect", "");
               chat.accept(EndingConnections);

               try {
                  Thread.sleep(200L);
               } catch (InterruptedException var3) {
                  var3.printStackTrace();
               }
            }
         } catch (Exception var4) {
            var4.printStackTrace();
         }

      });
      primaryStage.show();
   }

   public static boolean StartConnectionWithServer(String serverip, String port) {
      if (chat == null) {
         chat = new ClientController(serverip, Integer.parseInt(port));
         return true;
      } else {
         System.out.println("Already connected to the server.");
         return false;
      }
   }
}
