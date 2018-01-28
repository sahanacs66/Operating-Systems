/**
 * Copyright (c) 1992-1993 The Regents of the University of California.
 * All rights reserved.  See copyright.h for copyright notice and limitation 
 * of liability and disclaimer of warranty provisions.
 *  
 *  Created by Patrick McSweeney on 12/5/08.
 */
package jnachos.kern;

import jnachos.filesystem.JavaFileSystem;
import jnachos.filesystem.JavaOpenFile;
import jnachos.filesystem.OpenFile;
import jnachos.machine.*;
import org.omg.Messaging.SYNC_WITH_TRANSPORT;

import javax.crypto.Mac;
import javax.jnlp.JNLPRandomAccessFile;
import java.nio.file.FileSystem;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;

/** The class handles System calls made from user programs. */
public class SystemCallHandler {
	/** The System call index for halting. */
	public static final int SC_Halt = 0;


	/** The System call index for exiting a program. */
	public static final int SC_Exit = 1;

	/** The System call index for executing program. */
	public static final int SC_Exec = 2;

	/** The System call index for joining with a process. */
	public static final int SC_Join = 3;

	/** The System call index for creating a file. */
	public static final int SC_Create = 4;

	/** The System call index for opening a file. */
	public static final int SC_Open = 5;

	/** The System call index for reading a file. */
	public static final int SC_Read = 6;

	/** The System call index for writting a file. */
	public static final int SC_Write = 7;

	/** The System call index for closing a file. */
	public static final int SC_Close = 8;

	/** The System call index for forking a forking a new process. */
	public static final int SC_Fork = 9;

	/** The System call index for yielding a program. */
	public static final int SC_Yield = 10;

	public static final int SC_SendMsg = 13;

	public static final int SC_WaitMsg = 14;

	public static final int SC_SendAns = 15;

	public static final int SC_WaitAns = 16;
	/**
	 * Entry point into the Nachos kernel. Called when a user program is
	 * executing, and either does a syscall, or generates an addressing or
	 * arithmetic exception.
	 * 
	 * For system calls, the following is the calling convention:
	 * 
	 * system call code -- r2 arg1 -- r4 arg2 -- r5 arg3 -- r6 arg4 -- r7
	 * 
	 * The result of the system call, if any, must be put back into r2.
	 * 
	 * And don't forget to increment the pc before returning. (Or else you'll
	 * loop making the same system call forever!
	 * 
	 * @pWhich is the kind of exception. The list of possible exceptions are in
	 *         Machine.java
	 **/
	public static void handleSystemCall(int pWhichSysCall) {

		System.out.println("SysCall:" + pWhichSysCall);
		boolean flag = false;

		switch (pWhichSysCall) {
		// If halt is received shut down
		case SC_Halt:
			Debug.print('a', "Shutdown, initiated by user program.");
			Interrupt.halt();
			break;

		case SC_Exit:
			// Read in any arguments from the 4th register
			int arg = Machine.readRegister(4);

			NachosProcess endingProcess = JNachos.getCurrentProcess();
			LinkedList<MsgBuffer> msgBuffer3 = endingProcess.getMsgQueue();
			if(msgBuffer3.isEmpty()){
				endingProcess.finish();
			}
			else{
				for(MsgBuffer msgBuffer1: endingProcess.getMsgQueue()){
					System.out.println(" dummy message");

				}
				endingProcess.finish();
			}



			break;


			case SC_Fork:
				int addr = Machine.readRegister(4);
				NachosProcess child = new NachosProcess("child");
				child.setSpace(new AddrSpace((JNachos.getCurrentProcess().getSpace())));
				Machine.writeRegister(2,child.getPID());
				int pc = Machine.readRegister(Machine.NextPCReg) + 4;
				int nextc = Machine.readRegister(Machine.NextPCReg);
				Machine.mRegisters[Machine.PrevPCReg] = Machine.mRegisters[Machine.PCReg];
				Machine.mRegisters[Machine.PCReg] = Machine.mRegisters[Machine.NextPCReg];
				Machine.mRegisters[Machine.NextPCReg] = pc;
				child.saveUserState();
				child.writeRegister(2, 0);
				child.setShouldRestoreDefault(false);
				System.out.println("Forking child process: name--> "+ child.getName() + " with PID --> " + child.getPID());
				child.fork(new VoidFunctionPtr() {
					@Override
					public void call(Object pArg) {
						child.getSpace().restoreState();
						Machine.run();
					}
				}, null);
				break;

			case SC_Join:
				NachosProcess currentProcess = JNachos.getCurrentProcess();
				int PPid= currentProcess.getPID();
				int PID = Machine.readRegister(4);
				System.out.println("current process name--> " + currentProcess.getName() + " called join on the process with PID --> " + PID);
				if(JNachos.getMprocessTable().containsKey(PID) && PID != PPid){
					JNachos.setmWaitingTable(PID,PPid);
					currentProcess.sleep();
				} else {
					currentProcess.setExitstatus(-1);
				}
				int pgmc = Machine.readRegister(Machine.PCReg);
				int nextcount = Machine.readRegister(Machine.NextPCReg);
				Machine.writeRegister(Machine.PrevPCReg,pgmc);
				Machine.writeRegister(Machine.PCReg,nextcount);
				Machine.writeRegister(Machine.NextPCReg,(nextcount+4));
				break;


			case SC_Exec:
				int address = Machine.readRegister(4);
				StringBuffer filename=new StringBuffer();
				int charFilename;

				while((charFilename = Machine.mMainMemory[address++]) != 0)
					filename.append((char) charFilename);
				OpenFile executable = JNachos.mFileSystem.open(filename.toString());
				if (executable == null) {
					Debug.print('t', "Unable to open file" + filename);
					return;
				}

				// Load the file into the memory space
				AddrSpace space = new AddrSpace(executable);
				JNachos.getCurrentProcess().setSpace(space);

				// set the initial register values
				space.initRegisters();

				// load page table register
				space.restoreState();
				JNachos.getCurrentProcess().restoreUserState();

				// jump to the user progam
				// machine->Run never returns;
				Machine.run();

				break;



			case SC_SendMsg:
				int pgmcounter = Machine.readRegister(Machine.PCReg);
				int nextcounter = Machine.readRegister(Machine.NextPCReg);
				System.out.println("Send MSG");
				Machine.writeRegister(Machine.PrevPCReg,pgmcounter);
				Machine.writeRegister(Machine.PCReg,nextcounter);
				Machine.writeRegister(Machine.NextPCReg,(nextcounter+4));
				int receiverId = (int) JNachos.procNameToID.get(extractArguements(Machine.readRegister(4)));
				//Debug.print('r', String.valueOf(receiverId));
				String messageRead = extractArguements(Machine.readRegister(5));
				MsgBuffer freeBuffer = new MsgBuffer();
				for (MsgBuffer msgBuffer : JNachos.bufferPool){
				    if (msgBuffer.getEmpty()){
				        freeBuffer = msgBuffer;
				        flag = true;
				        break;
                    }
                }
                if (flag) {
                    freeBuffer.setMessage(messageRead);
                    freeBuffer.setSenderId(JNachos.getCurrentProcess().getPID());
                    NachosProcess receiver = (NachosProcess) JNachos.getMprocessTable().get(receiverId);
                    receiver.addMsgToQueue(freeBuffer);
                    freeBuffer.setEmpty(false);
                    System.out.println("message sent to" + " " + "process name" + " " + "'" + receiver.getName() +"'"+ "is" + " " + freeBuffer.getMessage());
                    if (receiver.isMsgFlag()){
                    	System.out.println("process Activating " + receiver.getName());
                    	receiver.setMsgFlag(false);
                    	Scheduler.readyToRun(receiver);
					}
                    Machine.writeRegister(2, freeBuffer.getId());
                }
                else
                    Machine.writeRegister(2,-1);

				break;


			case SC_WaitMsg:
				int pgmcounter2 = Machine.readRegister(Machine.PCReg);
				int nextcounter2 = Machine.readRegister(Machine.NextPCReg);
				System.out.println("Wait MSG");
				Machine.writeRegister(Machine.PrevPCReg,pgmcounter2);
				Machine.writeRegister(Machine.PCReg,nextcounter2);
				Machine.writeRegister(Machine.NextPCReg,(nextcounter2+4));
				NachosProcess invokingProcess = JNachos.getCurrentProcess();
			    if(invokingProcess.getMsgQueue().isEmpty()) {
			        invokingProcess.setMsgFlag(true);
			        System.out.println("Putting to sleep " + invokingProcess.getName());
					invokingProcess.sleep();
                }
                else {
			    	MsgBuffer received = invokingProcess.getMsgQueue().getFirst();
			    	System.out.println("message received" + " " + "by" + " " + "process name" + " " + "'"+invokingProcess.getName()+"'" + " "+ "is" + " " +received.getMessage());

				}

				break;

			case SC_SendAns:
				int pgmcounter3 = Machine.readRegister(Machine.PCReg);
				int nextcounter3 = Machine.readRegister(Machine.NextPCReg);

				Machine.writeRegister(Machine.PrevPCReg,pgmcounter3);
				Machine.writeRegister(Machine.PCReg,nextcounter3);
				Machine.writeRegister(Machine.NextPCReg,(nextcounter3+4));
                NachosProcess answeringProcess = JNachos.getCurrentProcess();
                String ansread = extractArguements(Machine.readRegister(4));
                MsgBuffer buffer = answeringProcess.getMsgQueue().removeFirst();
				buffer.setAnswer(ansread);
				NachosProcess actualSender = (NachosProcess) JNachos.getMprocessTable().get(buffer.getSenderId());
				System.out.println("answer sent to " + " " +  "'"+actualSender.getName() + "'" + " " + "is " + ansread );
				JNachos.answerTracker.put(actualSender,buffer);
				if (actualSender.isAnsFlag()){
					System.out.println("Activating " + actualSender.getName());
					actualSender.setAnsFlag(false);
					Scheduler.readyToRun(actualSender);
				}
				break;

			case SC_WaitAns:
				int pgmcounter4 = Machine.readRegister(Machine.PCReg);
				int nextcounter4 = Machine.readRegister(Machine.NextPCReg);

				System.out.println("IN WAIT ANSWER");
				Machine.writeRegister(Machine.PrevPCReg,pgmcounter4);
				Machine.writeRegister(Machine.PCReg,nextcounter4);
				Machine.writeRegister(Machine.NextPCReg,(nextcounter4+4));
                NachosProcess receivingproc = (NachosProcess) JNachos.getMprocessTable().get(JNachos.getCurrentProcess().getPID());
                if (JNachos.answerTracker.containsKey(receivingproc) == false){
                	receivingproc.setAnsFlag(true);
					receivingproc.sleep();
				}
				else {
					MsgBuffer msgBuffer = (MsgBuffer) JNachos.answerTracker.get(receivingproc);
					System.out.println("Answer received by " + "process name " + "'" + receivingproc.getName()+"'"+ " " + msgBuffer.getAnswer());
					msgBuffer.setEmpty(true);
				}

				break;


		default:
			Interrupt.halt();
			break;

		}
	}

	private static String extractArguements(int locator){
	    String extractedValue = new String();
	    int locatedValue = 1;
	    while ((char) locatedValue != '\0'){
	        locatedValue = Machine.readMem(locator,1);
	        if ((char) locatedValue != '\0')
	            extractedValue = extractedValue + (char)locatedValue;
            locator++;
	    }
	    return extractedValue;
    }
}
