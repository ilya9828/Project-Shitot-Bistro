# Project-Shitot-Bistro

A comprehensive **JavaFX client-server** application designed to manage the end-to-end workflow of a modern bistro.  
The system streamlines operations including reservations, waiting lists, customer check-ins, payments, and managerial reporting.

## Project Structure

The repository is organized as an Eclipse multi-project workspace to keep a clear separation of concerns:

- **`Client_GUI`**: JavaFX desktop client, FXML views, and UI controllers.
- **`server`**: Backend logic, database connectivity, and request handling.
- **`Common`**: Shared entities, DTOs, and models used by both client and server.
- **`OCSF`**: Object Client-Server Framework used for asynchronous socket communication.
- **`G3_Assignment2`**: Project documentation and assignment deliverables.

## Tech Stack

- **Language:** Java (JavaSE-23)
- **UI Framework:** JavaFX
- **Database:** MySQL (persistent storage)
- **Networking:** OCSF-style socket communication
- **IDE:** Eclipse (includes `.project` and `.classpath` configuration files)

## Prerequisites

Make sure the following are installed before setup:

- **JDK 23** or newer
- **JavaFX SDK** (configured in your local environment)
- **MySQL Server** (local instance on `localhost:3306`)
- **MySQL Connector/J** (`9.5.0` recommended)

## Setup and Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/ilya9828/Project-Shitot-Bistro.git
   ```
2. **Import projects into Eclipse**
   - `Client_GUI`
   - `server`
   - `Common`
   - `OCSF`
3. **Configure environment**
   - Fix JavaFX library paths in `.classpath` if needed.
   - Create a MySQL database named `bistrodb`.
   - Update DB credentials in `server/src/Server/mysqlConnection.java`.
4. **Security note**
   - Avoid committing credentials to GitHub.
   - Prefer environment variables or external config files.

## Running the Application

Start components in this order:

1. **Launch server**
   - Run `server/src/Server/ServerUI.java`
   - Set the port and start listening
2. **Launch client**
   - Run `Client_GUI/src/client/ClientUI.java`
   - Enter the server IP and port in the connection screen

## Main Features

- **Reservation Management:** Create, update, and track customer bookings.
- **Waiting List Logic:** Handle walk-ins when the bistro is fully booked.
- **Role-Based Flow:** Dedicated menus and permissions for guests, staff, and managers.
- **Advanced Reporting:** Visual charts for delays and reservation trends.
- **Integrated Billing:** Payment handling and order history views.

## Author

Developed by **Ido Peretz** as part of the Software Engineering curriculum at Braude College.
