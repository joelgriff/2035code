/*
 * Replace the following string of 0s with your student number
 * c230184125
 */

import java.io.*;
import java.net.*;

public class Protocol {

	static final String NORMAL_MODE = "nm"; // normal transfer mode: (for Part 1 and 2)
	static final String TIMEOUT_MODE = "wt"; // timeout transfer mode: (for Part 3)
	static final String GBN_MODE = "gbn";    // GBN transfer mode: (for Part 4)
	static final int DEFAULT_TIMEOUT = 10;     // default timeout in seconds (for Part 3)
	static final int DEFAULT_RETRIES = 4;    // default number of consecutive retries (for Part 3)

	/*
	 * The following attributes control the execution of a transfer protocol and provide access to the
	 * resources needed for a file transfer (such as the file to transfer, etc.)
	 *
	 */

	private InetAddress ipAddress;      // the address of the server to transfer the file to. This should be a well-formed IP address.
	private int portNumber;            // the  port the server is listening on
	private DatagramSocket socket;     // The socket that the client bind to
	private String mode;               //mode of transfer normal/with timeout/GBN

	private File inputFile;           // The client-side input file to transfer  
	private String inputFileName;      // the name of the client-side input file for transfer to the server
	private String outputFileName;    //the name of the output file to create on the server as a result of the file transfer
	private long fileSize;            // the size of the client-side input file  

	private Segment dataSeg;         // the protocol data segment for sending segments with payload read from the input file to the server
	private Segment ackSeg;           //the protocol ack segment for receiving ACKs from the server
	private int maxPayload;                //The max payload size of the data segment
	private long remainingBytes;       //the number of bytes remaining to be transferred during execution of a transfer. This is set to the input file size at the start

	private int timeout;          //the timeout in seconds to use for the protocol with timeout (for Part 3)
	private int maxRetries;       //the maximum number of consecutive retries (retransmissions) to allow before exiting the client (for Part 3)(This is per segment)

	private int sentBytes;       //the accumulated total bytes transferred to the server as the result of a file transfer
	private float lossProb;      //the probability of corruption of a data segment during the transfer  (for Part 3)
	private int currRetry;       //the current number of consecutive retries (retransmissions) following a segment corruption (for Part 3)(This is per segment)
	private int totalSegments;   //the accumulated total number of ALL data segments transferred to the server as the result of a file transfer
	private int resentSegments;  //the accumulated total number of data segments resent to the server as a result of timeouts during a file transfer (for Part 3)

	/**************************************************************************************************************************************
	 **************************************************************************************************************************************
	 * For this assignment, you have to implement the following methods:
	 *		sendMetadata()
	 *      readData()
	 *      sendData()
	 *      receiveAck()
	 *      sendDataWithError()
	 *      sendFileWithTimeout()
	 *		sendFileWithGBN()
	 * Do not change any method signatures and do not change any other methods or code provided.
	 ***************************************************************************************************************************************
	 **************************************************************************************************************************************/
	/*
	 * This method sends protocol metadata to the server.
	 * Sending metadata starts a transfer by sending the following information to the server in the metadata object (defined in MetaData.java):
	 *      size - the size of the file to send
	 *      name - the name of the file to create on the server
	 *      maxSegSize - The size of the payload of the data segment
	 * deal with error in sending
	 * output relevant information messages for the user to follow progress of the file transfer.
	 * This method does not set any of the attributes of the protocol.
	 */
	public void sendMetadata() {
		MetaData metaData = new MetaData();
		metaData.setName(inputFileName);
		metaData.setSize(fileSize);
		metaData.setMaxSegSize(this.maxPayload);

		ByteArrayOutputStream outputStream = null;
		ObjectOutputStream objectOutputStream = null;
		try {

			outputStream = new ByteArrayOutputStream();
			objectOutputStream = new ObjectOutputStream(outputStream);

			objectOutputStream.writeObject(metaData);
			byte[] ToSend = outputStream.toByteArray();

			DatagramPacket Data_packet = new DatagramPacket(ToSend, ToSend.length, this.ipAddress, this.portNumber); // Assume serverAddress and serverPort are set

			socket.send(Data_packet);
			System.out.println("CLIENT --> Metadata sent successfully: " + metaData.getName() + ", size: " + metaData.getSize() + ", Max Segment Size: " + metaData.getMaxSegSize());

		} catch (IOException e) {
			System.err.println("CLIENT --> Error with sending metadata: " + e.getMessage());
		} finally {
			try {
				if (objectOutputStream != null) {
					objectOutputStream.close();
				}
				if (outputStream != null) {
					outputStream.close();
				}
			} catch (IOException e) {
				System.err.println("CLIENT --> Error with closing resources: " + e.getMessage());
			}
		}
	}


	/* 
	 * This method:
	 *  	read the next chunk of data from the file into the data segment (dataSeg) payload. 
	 *  	set the correct type of the data segment
	 *  	set the correct sequence number of the data segment.
	 *  	set the data segment's size field to the number of bytes read from the file
	 * This method DOES NOT:
	 * set the checksum of the data segment.
	 * The method returns -1 if this is the last data segment (no more data to be read) and 0 otherwise.
	 */
	public int readData() throws IOException {
		byte[] TotalPayload = new byte[maxPayload];
		int ReadBytes = InputStream.nullInputStream().read(TotalPayload);
		int sequenceNum = 0;

		if (ReadBytes == -1) {
			return -1;
		}

		dataSeg.setPayLoad(new String(TotalPayload, 0, ReadBytes));
		dataSeg.setSize(ReadBytes);
		dataSeg.setSq(sequenceNum);
		dataSeg.setType(SegmentType.Data);

		sequenceNum = (sequenceNum + 1) % 2;
        return 0;
    }

	/* 
	 * This method sends the current data segment (dataSeg) to the server 
	 * This method:
	 * 		computes a checksum of the data and sets the data segment's checksum prior to sending. 
	 * output relevant information messages for the user to follow progress of the file transfer.
	 */

	public Segment getCurrentSeg() {
		return dataSeg;
	}


	public void sendData() throws IOException {
		int CheckSum = checksum(dataSeg.getPayLoad(), false);
		dataSeg.setChecksum(CheckSum);

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		ObjectOutputStream OOS = new ObjectOutputStream(outputStream);
		OOS.writeObject(dataSeg);
		byte[] ToSend = outputStream.toByteArray();

		DatagramPacket PacketToSend = new DatagramPacket(ToSend, ToSend.length, ipAddress, portNumber);
		socket.send(PacketToSend);
		System.out.println("CLIENT --> Sent Segment with sq:" + dataSeg.getSq());
		System.out.println("INFO: Size -->" + dataSeg.getSize());
		System.out.println("INFO: Checksum -->" + dataSeg.getChecksum());

		sentBytes += dataSeg.getSize();
		totalSegments++;

		remainingBytes -= dataSeg.getSize();

		System.out.println("CLIENT --> Bytes sent so far: " + sentBytes);
		System.out.println("CLIENT --> Bytes needing to be sent: " + remainingBytes);
	} 


	//Decide on the right place to :
	// *  	update the remaining bytes so that it records the remaining bytes to be read from the file after this segment is transferred. When all file bytes have been read, the remaining bytes will be zero
	// *    update the number of total sent segments
	// *    update the number of sent bytes


	/* 
	 * This method receives the current Ack segment (ackSeg) from the server 
	 * This method:
	 * 		needs to check whether the ack is as expected
	 * 		exit of the client on detection of an error in the received Ack
	 * return true if no error
	 * output relevant information messages for the user to follow progress of the file transfer.
	 */
	public boolean receiveAck(int expectedDataSq)  {
		try {
			byte[] buffer = new byte[maxPayload];
			DatagramPacket AckPacket = new DatagramPacket(buffer, buffer.length);

			socket.receive(AckPacket);

			ByteArrayInputStream ByteStream = new ByteArrayInputStream(buffer);
			ObjectInputStream ObjectStream = new ObjectInputStream(ByteStream) ;
			Segment AckSeg = (Segment) ObjectStream.readObject();

			if (ackSeg.getSq() == expectedDataSq) {
				System.out.println("CLIENT --> Received correct Ack with sqNum: " + ackSeg.getSq());
				return true;
			} else {
				System.err.println("ERROR!! --> Ack received with incorrect sqNum: " + ackSeg.getSq());
				System.exit(1);
			}


		} catch (IOException | ClassNotFoundException e) {
			System.err.println("ERROR --> failed to receive Ack --> " + e.getMessage());
            System.exit(1);
        }

		return false;

	} 

	/* 
	 * This method sends the current data segment (dataSeg) to the server with errors
	 * This method:
	 * 	 	may  corrupt the checksum according to the loss probability specified if the transfer mode is with timeout (wt) 
	 * 		If the count of consecutive retries/retransmissions exceeds the maximum number of allowed retries, the method exits the client with an
	 * appropriate error message.
	 *	This method does not receive any segment from the server
	 * output relevant information messages for the user to follow progress of the file transfer.
	 */
	public void sendDataWithError() throws IOException {
		System.exit(0);
	} 

	/* 
	 * This method transfers the given file using the resources provided by the protocol structure.
	 *
	 * This method is similar to the sendFileNormal method except that it resends data segments if no ACK for a segment is received from the server. 
	 * This method:
	 *  simulates network corruption of some data segments by injecting corruption into segment checksums (using sendDataWithError() method).
	 *  will timeout waiting for an ACK for a corrupted segment and will resend the same data segment. 
	 *  updates attributes that record the progress of a file transfer. This includes the number of consecutive retries for each segment.
	 *
	 * output relevant information messages for the user to follow progress of the file transfer.
	 * after completing the file transfer, display total segments transferred and the total number of resent segments
	 * 
	 * relevant methods that need to be used include: readData(), sendDataWithError(), receiveAck().
	 */
	void sendFileWithTimeout() throws IOException 
	{
		System.exit(0);
	} 
	/*
	 *  transfer the given file using the resources provided by the protocol structure using GoBackN.
	 */
	void sendFileNormalGBN(int window) throws IOException
	{  
		System.exit(0);
	}	

	/*************************************************************************************************************************************
	 **************************************************************************************************************************************
	 **************************************************************************************************************************************
	These methods are implemented for you .. Do NOT Change them
	 **************************************************************************************************************************************
	 **************************************************************************************************************************************
	 **************************************************************************************************************************************/	 
	/* 
	 * This method initialises ALL the 19 attributes needed to allow the Protocol methods to work properly
	 */
	public void initProtocol(String hostName , String portNumber, String fileName, String outputFileName, String payloadSize, String mode) throws UnknownHostException, SocketException {
		this.portNumber = Integer.parseInt(portNumber);
		this.ipAddress = InetAddress.getByName(hostName);
		this.socket = new DatagramSocket();
		this.inputFile = checkFile(fileName);
		this.inputFileName = fileName;
		this.outputFileName =  outputFileName;
		this.fileSize       =this.inputFile.length();

		this.remainingBytes = this.fileSize;
		this.maxPayload = Integer.parseInt(payloadSize);
		this.mode = mode;
		this.dataSeg = new Segment();
		this.ackSeg = new Segment();

		this.timeout = DEFAULT_TIMEOUT;
		this.maxRetries = DEFAULT_RETRIES;

		this.sentBytes = 0;
		this.lossProb =0;
		this.totalSegments =0;
		this.resentSegments = 0;
		this.currRetry = 0;		
	}

	/* transfer the given file using the resources provided by the protocol
	 *      attributes, according to the normal file transfer without timeout
	 *      or retransmission (for part 2).
	 */
	public void sendFileNormal() throws IOException { 
		while (this.remainingBytes!=0) {
			readData(); 
			sendData();
			if(!receiveAck(this.dataSeg.getSq()))  System.exit(0);
		}
		System.out.println("Total Segments "+ this.totalSegments );  
	}

	/* calculate the segment checksum by adding the payload 
	 * Parameters:
	 * payload - the payload string
	 * corrupted - a boolean to indicate whether the checksum should be corrupted
	 *      to simulate a network error 
	 *
	 * Return:
	 * An integer value calculated from the payload of a segment
	 */
	public static int checksum(String payload, Boolean corrupted)
	{
		if (!corrupted)  
		{
			int i;

			int sum = 0;
			for (i = 0; i < payload.length(); i++)
				sum += (int)payload.charAt(i);
			return sum;
		}
		return 0;
	}

	/* used by Client.java to set the loss probability (for part 3)*/
	public void setLossProb(float loss) {
		this.lossProb = loss;
	}

	/* 
	 * returns true with the given probability 
	 * 
	 * The result can be passed to the checksum function to "corrupt" a 
	 * checksum with the given probability to simulate network errors in 
	 * file transfer.
	 *
	 */
	private static Boolean isCorrupted(float prob) {

		double randomValue = Math.random();  //0.0 to 99.9
		return randomValue <= prob;
	}

	/* check if the input file does exist before sending it */
	private static File checkFile(String fileName)
	{
		File file = new File(fileName);
		if(!file.exists()) {
			System.out.println("SENDER: File does not exists"); 
			System.out.println("SENDER: Exit .."); 
			System.exit(0);
		}
		return file;
	}
}
