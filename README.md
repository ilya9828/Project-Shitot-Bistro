# Project-Shitot-Bistro

A robust bistro management system designed with a professional client-server architecture. This project demonstrates advanced software engineering principles, networking protocols, and a modular approach to full-stack Java development.

## Overview
**Project-Shitot-Bistro** utilizes the **Object Client-Server Framework (OCSF)** to facilitate real-time communication between the client-side interface and the backend server. The system is designed to handle multiple concurrent users, ensuring data integrity and high performance in a restaurant environment.

## Repository Structure
The project is organized into modular components to ensure a clean separation of concerns, as seen in the repository files (e.g., **image_8fc5f8.png**):

*   **`Client_GUI`**: The front-end application layer, responsible for user interactions and visual data representation.
*   **`server`**: The central logic hub that processes requests, manages database interactions, and maintains system state.
*   **`Common`**: A shared library containing Data Transfer Objects (DTOs) and utility classes used by both the client and server to ensure synchronized communication.
*   **`OCSF`**: The core networking framework implementation.
*   **`G3_Assignment`**: Project deliverables and modular components developed throughout the project's milestones.

## Tech Stack
*   **Language:** Java
*   **Architecture:** Distributed Client-Server
*   **Frameworks:** OCSF (Object Client-Server Framework), JavaFX/Swing for GUI
*   **Database:** Integrated SQL-based storage (e.g., MySQL)
*   **Version Control:** Git

## Installation & Setup
1.  **Clone the Repository:**
    ```bash
    git clone [https://github.com/ilya9828/Project-Shitot-Bistro.git]

    Server Initialization:

Navigate to the /server directory.

Update the database configuration file with your local credentials.

Run the main Server class to start listening for incoming client requests.

Client Launch:

Navigate to the /Client_GUI directory.

Run the main application class.

Enter the Server IP address and the designated port to establish a connection.

Key Features
Concurrency: Efficiently manages multiple simultaneous client connections.

Real-time Updates: Immediate synchronization between the server and all active clients.

Modular Architecture: Clean, maintainable code following industry-standard design patterns.

Data Integrity: Robust handling of bistro operations, from order management to status tracking.
