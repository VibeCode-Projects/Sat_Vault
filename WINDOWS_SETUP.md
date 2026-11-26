# SatVault — Windows Setup Guide (From Scratch)

Follow these steps to run **SatVault** on a fresh Windows PC.

---

## 1. Install Prerequisites

You only need **two** tools (Maven is already included via `mvnw.cmd`):

1. **Java 25 (JDK 25)**
   - Install via PowerShell (`winget`) or download from [Adoptium](https://adoptium.net/):
     ```powershell
     winget install EclipseAdoptium.Temurin.25.JDK
     ```
   - Verify in a new terminal:
     ```powershell
     java -version
     ```

2. **Docker Desktop for Windows**
   - Install via PowerShell (`winget`) or download from [Docker Desktop](https://www.docker.com/products/docker-desktop/):
     ```powershell
     winget install Docker.DockerDesktop
     ```
   - **Open Docker Desktop** and wait until the bottom-left status says **Engine running**.

3. **Git** *(optional, if cloning via terminal)*:
   ```powershell
   winget install Git.Git
   ```

---

## 2. Get the Project

Open **PowerShell** or **Command Prompt** and run:

```powershell
git clone https://github.com/VibeCode-Projects/Sat_Vault.git
cd Sat_Vault
```

*(Or download the ZIP from GitHub, extract it, and open the folder.)*

---

## 3. Run Everything with One Command

Simply double-click **`start.bat`** in the `Sat_Vault` folder, or run it from the terminal:

```cmd
start.bat
```

`start.bat` automatically:
1. Checks that Java and Docker Desktop are running.
2. Starts the `mysql:8.4` container (`satvault-mysql`).
3. Waits until MySQL is healthy.
4. Launches the Spring Boot server on **http://localhost:8080**.

*(The first run downloads the `mysql:8.4` Docker image and Maven dependencies, so it may take 1–2 minutes. Subsequent runs are much faster.)*

---

## 4. Use SatVault

1. Open **http://localhost:8080** in your browser.
2. Click **Register** to create your account.
3. Upload, categorize, encrypt, lock, search, preview, and download your documents.

---

## Useful Commands

- **Stop the app**: Press `Ctrl + C` in the terminal window.
- **Stop MySQL container** (keeps your database data):
  ```cmd
  docker compose stop
  ```
- **Reset everything** (deletes database volume):
  ```cmd
  docker compose down -v
  ```
