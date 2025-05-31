# Run this Command for the first time while setting up google cloud cli

```bash
gcloud auth application-default login
```
# You also need to make sure the Vertex AI is enabled:

```bash
gcloud services enable aiplatform.googleapis.com
```
# Whenever you are adding new implementation in build.gradle 
# Create the Gradle wrapper:

```bash
gradlew wrapper
```

# To check if everything is in place after wrapper command run 'run' command

```bash
gradlew run
```

# To run the project go to the path where you have saved the project for mine it was

```bash
cd "C:\Users\2310300\gemini-workshop-for-java-developers"
```

# Now Run the server

```bash
gradlew -q run -DjavaMainClass=gemini.workshop.Server
```



