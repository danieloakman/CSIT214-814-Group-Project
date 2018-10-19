/*
 * CSIT214/814 GROUP ALPHA
 */

package PlagiarismDetection;

import com.google.api.core.ApiFuture;
import com.google.firebase.auth.ExportedUserRecord;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.ListUsersPage;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.auth.UserRecord.CreateRequest;
import com.google.firebase.auth.UserRecord.UpdateRequest;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.SetOptions;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import org.apache.commons.validator.routines.EmailValidator;

public class Firebase {
    static public Firestore db;
    static public boolean isInitialised = false;
    static public User currentUser = new User();
    static public boolean loggedIn = false;
    
    /*
     *  Initialises the Firebase API. Returns true if successfully initialised, else returns false.
     *  Requires an internet connection to initialise properly.
     */
    static public void initialise () {
        try {
            FileInputStream serviceAccount = new FileInputStream("serviceAccountKey.json");
            FirebaseOptions options = new FirebaseOptions.Builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
//                .setServiceAccountId("firebase-adminsdk-x0na7@csit314-814-group-project.iam.gserviceaccount.com")
//                .setDatabaseUrl("https://csit314-814-group-project.firebaseio.com/")
                .build();
            FirebaseApp.initializeApp(options);
            db = FirestoreClient.getFirestore();
            System.out.println("Successfully initialised Firebase.");
        } catch (IOException ex) {
            System.err.println("Firebase.initialise() error: " + ex.getMessage());
        }
    }
    
    /*
    *   Tests/validates the connection to Firebase by attempting to get a user by a known email
    *   should should always be in the system.
    */
    /*static public void validateConnectionToFirebase () {
        new Thread(new Runnable() {
            @Override
            public void run() {
                this.
                try {
                    UserRecord user = getUserByEmail("test@test.com");
                    if (user != null) // if the user was found
                        isInitialised = true;
                    else // if the user wasn't found
                        isInitialised = true;
                } catch (InterruptedException | ExecutionException ex) {
                    // if an exception occurred with the test, then also return false
                    isInitialised = false;
                }
                
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        
                    }
                });
            }
        }).start();
    }*/
    
    static public void createUserWithEmailAndPassword (String email, String password) throws InterruptedException, ExecutionException {
        String uid = email.substring(0, email.indexOf("@")); // johnsmith@gmail.com becomes uid = johnsmith
        CreateRequest request = new CreateRequest()
            .setUid(uid)
            .setEmail(email)
            .setPassword(password);
        FirebaseAuth.getInstance().createUserAsync(request).get();
    }
    
    static public boolean isEmailValid (String email) {
        return EmailValidator.getInstance().isValid(email);
    }
    
    static public void deleteUserByEmail (String email) throws InterruptedException, ExecutionException {
        UserRecord userRecord = getUserByEmail(email);
        FirebaseAuth.getInstance().deleteUserAsync(userRecord.getUid()); 
    }
    
    static public UserRecord getUserByEmail (String email) throws InterruptedException, ExecutionException {
        return FirebaseAuth.getInstance(FirebaseApp.getInstance()).getUserByEmailAsync(email).get();
    }
    
    static public void updateUserPasswordByEmail (String email, String password) throws InterruptedException, ExecutionException {
        UserRecord userRecord = getUserByEmail(email);
        UpdateRequest request = new UpdateRequest(userRecord.getUid())
            .setPassword(password);
        FirebaseAuth.getInstance().updateUserAsync(request);
    }
    
    static public ArrayList<UserRecord> getAllUserRecords () throws InterruptedException, ExecutionException {
        ArrayList<UserRecord> userRecordList = new ArrayList();
        ListUsersPage page = FirebaseAuth.getInstance().listUsersAsync(null).get();
        while (page != null) {
            for (ExportedUserRecord user : page.getValues()) {
                userRecordList.add(user);
            }
            page = page.getNextPage();
        }
        return userRecordList;
    }
    
    static public boolean signIn (String email, String password) throws InterruptedException, ExecutionException {
        DocumentReference docRef = db.collection("Users").document(email);
        // asynchronously retrieve the document
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        if (document.exists()) {
            Map<String, Object> data = document.getData();
            String passwordToMatch = data.get("password").toString();
            if (passwordToMatch.equals(password)) {
                System.out.println("Password matches!");
                return true;
            }
        } else {
            System.out.println("No such user!");
            return false;
        }
        return false;
    }
    
    static public void storeDocumentInDatabase (String documentText, String nameOfUploader, String title) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        Document doc = new Document(title, dtf.format(now), documentText, nameOfUploader);
        // Store doc in folder "Documents" in the database
        ApiFuture<DocumentReference> addedDocRef = db.collection("Documents").add(doc);
        try {
            // Stores the title and Document id in the User's folder.
            // So for example, Users/test@test.com/uploadedDocuments/addedDocRef.getId() now has a string value = title
            HashMap<String,Object> documentMap = new HashMap<>();
            HashMap<String,Object> uploadedDocuments = new HashMap<>();
            documentMap.put(addedDocRef.get().getId(), title);
            uploadedDocuments.put("uploadedDocuments", documentMap);
            db.collection("Users").document(nameOfUploader).set(uploadedDocuments, SetOptions.merge());
            System.out.println("Added document with ID: " + addedDocRef.get().getId());
        } catch (InterruptedException | ExecutionException ex) {
            Logger.getLogger(Firebase.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    /*
    *   For signing in the user later.
    */
    static public void storePasswordInDatabase (String email, String password) {
        HashMap<String, String> data = new HashMap<>();
        data.put("password", password);
        db.collection("Users").document(email).set(data);
        System.out.println("Successfully stored email and password in database.");
    }
    
    
    static public void getDocumentFromDatabase () {
        // asynchronously retrieve all documents
        ApiFuture<QuerySnapshot> future = db.collection("Documents").get();
        // future.get() blocks on response
        List<QueryDocumentSnapshot> documents = null;
        try {
            documents = future.get().getDocuments();
        } catch (InterruptedException | ExecutionException ex) {
            Logger.getLogger(Firebase.class.getName()).log(Level.SEVERE, null, ex);
        }
        for (QueryDocumentSnapshot document : documents) {
            System.out.println(document.getId());
            System.out.println(document.getData());
        }
    }
}

class Document {
    public String timeOfUpload;
    public String text;
    public String uploader;
    public String title;

    public Document (String title ,String timeOfUpload, String text, String uploader) {
        this.title = title;
        this.timeOfUpload = timeOfUpload;
        this.text = text;
        this.uploader = uploader;
    }
}

class User {
    public String email;
    public String password;
    public boolean admin;
    public User () {
        email = "";
        password = "";
    }
    public User (String email, String password) {
        this.email = email;
        this.password = password;
    }
}