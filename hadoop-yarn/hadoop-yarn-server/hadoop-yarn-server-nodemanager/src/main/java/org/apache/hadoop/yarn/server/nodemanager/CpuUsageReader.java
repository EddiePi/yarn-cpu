package org.apache.hadoop.yarn.server.nodemanager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CpuUsageReader implements Runnable {
	float stealTimePercent = 0.0f;

	private static int DEFAULT_READ_INTERVAL = 1000;
	private float CPU_USAGE_PERCENT_FADE_FACROR;
	private long stealTime_1 = 0;
	private long stealTime_2 = 0;
	private long cpuTime_1 = 0;
	private long cpuTime_2 = 0;
	private boolean firstRead = true;
	private static String DEFAULT_FILE_NAME = "/proc/stat";
	private String FILE_NAME = null;
	public boolean isStopped = false;
	
	float usagePercent = 0.0f;
	private long usageTime_1 = 0;
	private long usageTime_2 = 0;
	
	float idlePercent = 0.0f;
	private long idleTime_1 = 0;
	private long idleTime_2 = 0;
	
	//variables used ONLY for test
	float userTimePercent = 0.0f;
	private long userTime_1 = 0;
	private long userTime_2 = 0;
	//end ONLY for test
	
	public CpuUsageReader(float usagePercentFadeFactor) {
		this.CPU_USAGE_PERCENT_FADE_FACROR = usagePercentFadeFactor;
	}
	
	public void run() {
		while(!isStopped) {
			try {
				parseCPUTimes(readProcStat());
				calculateUserTimePercent();
				calculateStealTimePercent();
				calculateUsagePercent();
				//pirntTimePercent();
				if (firstRead == true) {
					firstRead = false;
				}
				Thread.sleep(DEFAULT_READ_INTERVAL);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				//Do nothing
			}
		}
	}
	
//	public void startcpuInfoReader() {
//		cpuInfoReaderRunnable = new Runnable() {
//			public void run() {
//				while(!isStopped)
//				try {
//					parseCPUTimes(readProcStat());
//					calculateUserTimePercent();
//					calculateStealTimePercent();
//					pirntTimePercent();
//					if (firstRead == true) {
//						firstRead = false;
//					}
//					Thread.sleep(DEFAULT_READ_INTERVAL);
//				} catch (Throwable e) {
//					//Do nothing
//				}
//			}
//		};
//		cpuInfoReaderRunnable.run();
//	}
	
	public void setFileName(String name) {
		this.FILE_NAME = name;
	}
	
	public void stopCpuInfoReader() {
		isStopped = true;
	}
	
	/**
	 * Read file '/proc/stat' and parse the cpu time value info.
	 * 
	 * @return List<Integer>
	 * @author Eddie
	 */
	public List<Long> readProcStat() {
		String fileName = (this.FILE_NAME == null) ? DEFAULT_FILE_NAME : FILE_NAME;
		File file = new File(fileName);
		BufferedReader reader = null;
		List<Long> CPUTimes = new ArrayList<Long>();
		try {
            //System.out.println("以行为单位读取文件内容，一次读一整行：");
            reader = new BufferedReader(new FileReader(file));
            String totalCPUInfo = null;
            totalCPUInfo = reader.readLine();
            
//            String tempString = null;
//            int line = 1;
//            // 一次读入一行，直到读入null为文件结束
//            while ((tempString = reader.readLine()) != null) {
//                // 显示行号
//                System.out.println("line " + line + ": " + tempString);
//                line++;
//            }
            String[] units = totalCPUInfo.split(" ");
            int unitcount = 0;
            for(String unit : units) {
            	//System.out.println("unit: " + unit);
            	if (unitcount > 1) {
            		CPUTimes.add(Long.valueOf(unit));
            		//System.out.println("value: " + CPUTimes.get(unitcount));
            	}
            	unitcount++;
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
        	if (reader != null) {
        		try {
        			reader.close();
                } catch (IOException e1) {
                }
            }
        }
		return CPUTimes;
	}
	
	private void parseCPUTimes(List<Long> list) {
		Long totalTime = (long) 0;
		for (Long eachCPUTime : list) {
			totalTime += eachCPUTime;
		}
		if (!firstRead) {
			cpuTime_1 = cpuTime_2;
			stealTime_1 = stealTime_2;
			userTime_1 = userTime_2;
			usageTime_1 = usageTime_2;
			idleTime_1 = idleTime_2;
		}
		cpuTime_2 = totalTime;
		stealTime_2 = list.get(7).intValue();
		idleTime_2 = list.get(3).intValue();
		//USED ONLY FOR TEST
		userTime_2 = list.get(0).intValue();
		
		usageTime_2 = cpuTime_2 - idleTime_2;
		
	}
	
	
	//-----------------steal time percent
	private synchronized void calculateStealTimePercent() {
		if (!firstRead) {
			float weightedOldStealTimePercent = stealTimePercent * CPU_USAGE_PERCENT_FADE_FACROR;
			stealTimePercent = (weightedOldStealTimePercent +  (float) (stealTime_2 - stealTime_1)
					/ (cpuTime_2 - cpuTime_1)) / (1.0f + CPU_USAGE_PERCENT_FADE_FACROR);
		}
	}
	
	public float getStealTimePercent() {
		return stealTimePercent;
	}
	
	//-----------------usage percent
	private synchronized void calculateUsagePercent() {
		if (!firstRead) {
			float weightedOldUsagePercent = usagePercent * CPU_USAGE_PERCENT_FADE_FACROR;
			usagePercent = (weightedOldUsagePercent + (float) (usageTime_2 - usageTime_1)
					/ (cpuTime_2 - cpuTime_1)) / (1.0f + CPU_USAGE_PERCENT_FADE_FACROR);
		}
	}
	
	public float getUsagePercent() {
		return usagePercent;
	}
	
	//-----------------user time percent
	private synchronized void calculateUserTimePercent() {
		if (!firstRead) {
			float weightedOldUserTimePercent = userTimePercent * CPU_USAGE_PERCENT_FADE_FACROR;
			userTimePercent = (weightedOldUserTimePercent + (float) (userTime_2 - userTime_1)
					/ (cpuTime_2 - cpuTime_1)) / (1.0f + CPU_USAGE_PERCENT_FADE_FACROR);
		}
	}
	
	public float getUserTimePercent() {
		return userTimePercent;
	}
	
	public void pirntTimePercent() {
		System.out.println("steal time percentage: " + stealTimePercent);
		System.out.println("usage percentage: " + usagePercent);
		System.out.println("user time percentage: " + userTimePercent);
	}
}
