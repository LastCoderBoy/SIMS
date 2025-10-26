# SMART INVENTORY MANAGEMENT SYSTEM


## Setup Instructions

### 1. Clone the repository
```bash
git clone https://github.com/LastCoderBoy/SIMS.git
cd SIMS
```

### 2. Configure AWS Credentials

**IMPORTANT:** Never commit real AWS credentials to Git!

1. Navigate to `src/main/resources/`
2. Copy the example file:
```bash
   cp application-local.properties.example application-local.properties
```
3. Edit `application-local.properties` and add your AWS credentials:
```properties
   aws.access-key=YOUR_ACTUAL_ACCESS_KEY
   aws.secret-key=YOUR_ACTUAL_SECRET_KEY
   aws.region=us-east-1
   aws.s3.bucket-name=your-bucket-name
```

**Note:** The `application-local.properties` file is already in `.gitignore` and won't be committed.

### 3. Run the application
```bash
./mvnw spring-boot:run
```
