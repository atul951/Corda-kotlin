 # DGK Bank

### Introduction:
- DGK Bank is a small Corda Network that distribute a asset called DANIEL. 
- DANIEL is a non fungible asset that contain thoughts.

As per below diagram:

The BCS Learning corporation is issuing a request to the Bank of Daniel and Bank of Daniel in response sends a DANIEL. DANIEL contains a piece of text called a 'Thought'.

![Diagram](danielcorda.png)



### Getting Started:
These instructions will get you a copy of the project up and running on your local machine for development and testing purposes.  



### Prerequisites: 

The following items should be installed in your system: 

1.  [JDK 1.8](https://docs.oracle.com/javase/8/docs/technotes/guides/install/install_overview.html)
 

2.  [KOTLIN 1.2](https://kotlinlang.org/docs/tutorials/command-line.html)

### Installing: 
A step by step series of examples that tell you how to get a development deployment running. 
##### STEPS- 
Ensure that you have the build.gradle file with the deployNodes task in the project root. 

**1.To build the network's nodes** 

- Open a terminal in the project root directory.
 

- Run command
###### Windows
```
gradlew deployNodes
```
###### Unix
```
./gradlew deployNodes
```

**2 To run the development network:** 

- Navigate

```
./build/nodes.
```

- Run File 

```
./runnodes
```

- To make an issuance request to the bank, Run: 

```
./gradlew runBankClient -Pthought=<thought>
```

- To check if the transaction was successful, Run:

```
run vaultQuery contractStateType: com.dgkrajnik.bank.DanielState
```

- In the shell of the BCS learning node, and check for a valid DanielState in the output.


### Running the Tests: 
  **1 Unit Test:**
###### Windows
```
gradlew test
```
###### Unix
```
 ./gradlew test
 ```

 **2 Integration Test:**
 ###### Windows
 ```
 gradlew integrationTest
 ```
 ###### Unix
 ```
 ./gradlew integrationTest
 ```

### Built With: 
**1 Corda v2.0 -**  Framework 

**2 Intelij-**  IDE 

**3 Gradle-**  Dependency Management 

**4 Kotlin-**   Language 

### Author: 
**Corda Team-** For Template Code 

[**Daniel  Krajnik**](daniel.krajnik@bcstechnology.com.au) -Rest of the work 
 
 
 
### Acknowledgment:
-Hat tip to anyone who's code was used  
  
-Inspiration  
  
-YOU 














