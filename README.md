# STEP 1 ----> Go to Resource Folder and change the path of this key value pairs 'output.excel.file','output.text.file' according to your system (ONE TIME)

# STEP 2 ----> Run this Command for the first time while setting up google cloud cli( ONE TIME )

```bash
gcloud auth application-default login
```
# STEP 3 ---->You also need to make sure the Vertex AI is enabled: ( ONE TIME )

```bash
gcloud services enable aiplatform.googleapis.com
```
# STEP 4 ---->Whenever you are adding new implementation in build.gradle. Create the Gradle wrapper:

```bash
gradlew wrapper
```

# STEP 5 ---->To check if everything is in place after wrapper command run 'run' command

```bash
gradlew run
```

# STEP 6 ---->To run the project go to the path where you have saved the project for mine it was

```bash
cd "C:\Users\2310300\gemini-workshop-for-java-developers"
```

# STEP 7 ---->Now Run the server

```bash
gradlew -q run -DjavaMainClass=gemini.workshop.Server
```



