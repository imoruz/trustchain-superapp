# Spotify 1 - Team 4

Our goal for this project was to create a more fair way for artists to be paid. Normally, the top artists would get most of the money/donations. To address this imbalance, we introduced the concept of a Shared Wallet. Instead of donating to individual artists, users contribute to this Shared Wallet. Donations are then distributed based on a predefined "fair" algorithm, ensuring that smaller or emerging artists receive the support they need to grow.

## What have we done

### Shared Wallet Implementation
In this section we will describe how the we implemented the Shared Wallet. 

The Shared Wallet is built by using the ipv8 TrustChain Community class. The ipv8 protocol enables the creation of a peer-to-peer network between devices. Through this network, devices can directly communicate with one another, allowing us to establish and maintain the Shared Wallet.

Currently, the concept involves designating one device (phone) to act as the Shared Wallet. We've added a button in the app that allows a device to take on this role. At any given time, only one Shared Wallet can be active within the network.

We break down the implementation in 2 important functionalities: 

1. **Broadcasting:** The broadcasting step happens when a device wants to become a Shared Wallet. On clicking the "Become Shared Wallet" button we send 2 types of ipv8 messages. Firstly, we send a **SharedWalletMessage**. This message is used to propagate the information of the device that wants to become the Shared Wallet (public key, wallet id). Secondly, we send a **SharedWalletInfoMessage**. This message is used to share the current balance of the wallet and the transactions that have been made to this wallet. Whenever there is a change in balance or a new transaction occurs, we use a SharedWalletInfoMessage to keep track of these changes. This ensures that all connected devices stay up to date with the Shared Wallet's status.

2. **Discovering:** To receive these ipv8 messages that are sent duting the broadcast phase we have set up message handlers. The high level logic in these is pretty straightforward and in these handlers we basically keep track of the information that was received from the Shared Wallet device.

### Pro Rata
- pro rata -> talk about fairness

### User Centric
- user centric -> talk about fairness



## Future Work
Below we list some ideas for future improvements to our project:

- **Having an always online Shared Wallet.** Implementing an always-online Shared Wallet. Currently, the system depends on a device being online and manually selecting the "Become Shared Wallet" option. In the future, it would be ideal to have a dedicated device that is always available to fulfill this role continuously.

- **Optimize transaction fees.** Right now we use some estimations to account for the transaction fees. By having the Shared Wallet instead of multiple individual donations one could take a look at optimizing transaction fees, so as much money as possible goes to the artists.

- **More optimized message flow.** While we did take some optimizations into account and we did some basic benchmarking it could be interesting to dive deeper in the message flow and set it up in such a way that millions of devices could communicate in a swift and efficient way.

## What was good/went well?
In this section we will describe some things that went well during the development of the project. 

- The project allowed for a clear split of tasks for good efficiency in the team. 
- The teamwork was great and we were able to coordinate well and help each other out if needed.

## What was not clear?
In this section we will describe some struggles we had during the project.

- The codebase was quite large and overwhelming at times making it harder to get organized in the starting phase of the project.