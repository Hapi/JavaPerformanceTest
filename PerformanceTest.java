import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Console;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class PerformanceTest
{
	private final static String NAME = PerformanceTest.class.getSimpleName();
	private final static int DEFAULT_NUMBER_OF_THREADS = 10;
	private final static int RANGE = 1_000_000;
	private static final int LOWERBOUND = 33_550_300;
	private static final int UPPERBOUND = 33_550_400;
	private final static Path TARGET_DIR = Paths.get("__target__");
	private final static String FILENAMEPATTERN = "text-%03d.txt";
	private final static int NUMBER_OF_FILES = 200;
	private final static int TOTAL_NUMBER_OF_BYTES = 2_000_000_000;
	private final static int FILE_SIZE = TOTAL_NUMBER_OF_BYTES / NUMBER_OF_FILES;
	private final static int BUFFER_SIZE = 1024 * 8;
	private final static int NUMBER_OF_WRITES = FILE_SIZE / BUFFER_SIZE;
	private final static String ALPHA = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
	private final static int ALPHA_LEN = ALPHA.length();

	private static AtomicInteger _fileCount = new AtomicInteger();

	public static void main(String[] args)
	{
		OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
		final Cases cases = handleArgs(args, os.getAvailableProcessors());
		RuntimeMXBean jvm = ManagementFactory.getRuntimeMXBean();
		System.out.println("OS: " + os.getName() + ", " + os.getVersion() + " (" + os.getArch() + ")");
		System.out.println("Number of cores: " + os.getAvailableProcessors());
		System.out.println("JVM impl: " + jvm.getVmName() + ", " + jvm.getVmVersion() + " (" + jvm.getVmVendor() + ")");
		System.out.println("JVM spec: " + jvm.getSpecName() + ", " + jvm.getSpecVersion() + " (" + jvm.getSpecVendor() + ")");
		testCpu(cases);
		testFileSystem(cases);
		System.out.println();
		cases.printResults();
	}

	private static Cases handleArgs(String[] args, int numOfProcessors)
	{
		if(args.length == 0)
			return new Cases(numOfProcessors, 1, DEFAULT_NUMBER_OF_THREADS);
		if(args.length == 1) {
			final String arg = args[0];
			switch(arg) {
				case "-h" :
				case "--help" :
					printHelp();
					System.exit(0);
			}

			if(arg.matches("\\d+")) {
				final int numOfThreads = Integer.parseInt(arg);
				if(numOfThreads < 1) {
					System.out.println("! Number of threads must be at least one (1).");
					printUsage();
					System.exit(1);
				}
				return new Cases(numOfProcessors, numOfThreads);
			}
			if(arg.matches("\\d+-\\d+")) {
				String bounds[] = arg.split("-");
				int lowerBound = Integer.parseInt(bounds[0]);
				int upperBound = Integer.parseInt(bounds[1]);
				if(upperBound < lowerBound) {
					System.out.printf(
						"! Upper bound must be greater than lower bound (lower:%d, upper:%d).\n",
						lowerBound,
						upperBound
					);
					printUsage();
					System.exit(1);
				}
				return new Cases(numOfProcessors, lowerBound, upperBound);
			}

			System.out.println(
				"! The argument must be either a number of threads or a range of a number of threads."
			);
			printUsage();
			System.exit(1);
		}

		List<Integer> numOfThreadsList = new ArrayList<>();
		for(String arg : args) {
			if(arg.matches("\\p{Digit}+")) {
				final int numOfThreads = Integer.parseInt(arg);
				if(numOfThreads < 1) {
					System.out.println("! Number of threads must be at least one (1).");
					printUsage();
					System.exit(1);
				}
				numOfThreadsList.add(numOfThreads);
			}
			else {
				System.out.println("! An argument must be a number.");
				printUsage();
				System.exit(1);
			}
		}
		return new Cases(numOfProcessors, numOfThreadsList);
	}

	private static void printUsage()
	{
		System.out.println();
		System.out.println("Usage:");
		System.out.printf("  java %s [-h|--help|ARG1 [ARG2 ARG3 ... ARGn]]\n", NAME);
		System.out.println();
	}
	private static void printHelp()
	{
		System.out.println("This application tests CPU and file operations performance.");
		System.out.println();
		System.out.println("Depending on the arguments the test can take several minutes to complete.");
		System.out.println("Running tests single-threaded and then with four threads takes totally");
		System.out.println("about 73 seconds to run on Mac OS X 10.9, 2.5 GHz Intel i5, 8 GB RAM and SSD.");
		System.out.println("Running ten tests with one to ten threads per test takes about 98 seconds");
		System.out.println("when run on Mac OS X 10.16, 2.5 GHz Intel Core i9, 64 GB RAM and SSD.");
		printUsage();
		System.out.println("where:");
		System.out.println("  ARG1 = Either number of threads for a single test case or a range of threads");
		System.out.println("         for the multiple test cases. If a single number is given then a single");
		System.out.println("         test case will be run with the given number of threads. However, when");
		System.out.println("         when a range is given, then there will be number of tests with increasing");
		System.out.println("         number of threads. The lower bound of the range is the number of threads");
		System.out.println("         in the first test and the upper bound is the number of threads in the last");
		System.out.println("         test. The range is given as LOWER-UPPER, where:");
		System.out.println("            LOWER = the number of threads in the first test");
		System.out.println("            UPPER = the number of threads in the last test");
		System.out.println("            There will be total of (UPPER - LOWER + 1) number of tests.");
		System.out.println("         So, if ARG1 is 2-4 then the will be three tests. The first is run with two");
 		System.out.println("         threads, the second with three threads and the last one with four threads.");
		System.out.println("  ARG2,");
		System.out.println("  ARG3,");
		System.out.println("  ARGn = Number of threads of the corresponding test case. The number of tests equals");
		System.out.println("         to the number of given arguments. See Examples for more information.");
		System.out.println();
		System.out.println("NOTICE!");
		System.out.printf("  If no arguments are given then there will be %d tests each having an increasing\n", DEFAULT_NUMBER_OF_THREADS);
		System.out.printf("  number of threads beginning from one (1) and ending up to %d.\n", DEFAULT_NUMBER_OF_THREADS);
		System.out.printf("  This is same than running the following command:\n");
		System.out.printf("    java %s 1-%d\n", NAME, DEFAULT_NUMBER_OF_THREADS);
		System.out.println();
		System.out.println("Examples:");
		System.out.printf("  java %s --help\n", NAME);
		System.out.printf("  java %s\n", NAME);
		System.out.printf("  java %s 3\n", NAME);
		System.out.printf("  java %s 2-6\n", NAME);
		System.out.printf("  java %s 1 3 4 5 8\n", NAME);
	}

	private static void testCpu(Cases cases)
	{
		int caseNumber = 1;
		for(int numOfThreads : cases.numOfThreads()) {
			print("CPU Test - case: ", caseNumber);
			if(numOfThreads == 1)
				cases.setCpuTime(caseNumber - 1, countRange(LOWERBOUND, UPPERBOUND, new IsPerfect()));
			else {
				ExecutorService es = Executors.newFixedThreadPool(numOfThreads);
				cases.setCpuTime(caseNumber - 1, countRange(LOWERBOUND, UPPERBOUND, new IsPerfectConcurrent(es)));
				es.shutdown();
			}
			caseNumber++;
		}
		System.out.println();
	}

	private static void testFileSystem(Cases cases)
	{
		try {
			cleanupTarget();
			Files.createDirectories(TARGET_DIR);
			int caseNumber = 1;
			for(int numOfThreads : cases.numOfThreads()) {
				print("File Test - case: ", caseNumber);
				long start = System.currentTimeMillis();
				ExecutorService es = Executors.newFixedThreadPool(numOfThreads);
				createFiles(es);
				es.shutdown();
				es.awaitTermination(5, TimeUnit.MINUTES);
				cases.setFileWriteTime(caseNumber - 1, System.currentTimeMillis() - start);

				start = System.currentTimeMillis();
				es = Executors.newFixedThreadPool(numOfThreads);
				readFiles(es);
				es.shutdown();
				es.awaitTermination(5, TimeUnit.MINUTES);
				cases.setFileReadTime(caseNumber - 1, System.currentTimeMillis() - start);

				start = System.currentTimeMillis();
				es = Executors.newFixedThreadPool(numOfThreads);
				deleteFiles(es);
				es.shutdown();
				es.awaitTermination(5, TimeUnit.MINUTES);
				cases.setFileDeleteTime(caseNumber - 1, System.currentTimeMillis() - start);
				caseNumber++;
			}
			System.out.println();
		}
		catch(IOException e) {
			e.printStackTrace();
		}
		catch(InterruptedException e) {
			e.printStackTrace();
		}
		finally {
			cleanupTarget();
		}
	}

	private static void cleanupTarget()
	{
		try {
			for(int i = 0; i < NUMBER_OF_FILES; i++)
				Files.deleteIfExists(
					Paths.get(
						TARGET_DIR.toString(),
						String.format(FILENAMEPATTERN, i + 1)
					)
				);
			Files.deleteIfExists(TARGET_DIR);
		}
		catch(IOException e) {
			e.printStackTrace();
		}
	}

	private static void createFiles(ExecutorService es)
	{
		_fileCount.set(1);
		for(int i = 0; i < NUMBER_OF_FILES; i++)
			es.execute(
				new Runnable() {
					@Override
					public void run()
					{
						try(
							BufferedWriter bw =
								Files.newBufferedWriter(
									Paths.get(
										TARGET_DIR.toString(),
										String.format(FILENAMEPATTERN, _fileCount.getAndIncrement())
									),
									Charset.defaultCharset()
								)
						) {
							final char[] buf = new char[BUFFER_SIZE];
							for(int writeCount = 0; writeCount < NUMBER_OF_WRITES; writeCount++) {
								Random rnd = new Random();
								for(int n = 0; n < BUFFER_SIZE; n++)
									buf[n] = ALPHA.charAt(rnd.nextInt(ALPHA_LEN));
								bw.write(buf);
							}
						}
						catch(IOException e) {
							e.printStackTrace();
						}
					}
				}
			);
	}

	private static void readFiles(ExecutorService es)
	{
		_fileCount.set(1);
		for(int i = 0; i < NUMBER_OF_FILES; i++)
			es.execute(
				new Runnable() {
					@Override
					public void run()
					{
						try(
							BufferedReader br =
								Files.newBufferedReader(
									Paths.get(
										TARGET_DIR.toString(),
										String.format(FILENAMEPATTERN, _fileCount.getAndIncrement())
									),
									Charset.defaultCharset()
								)
						) {
							final char[] buf = new char[BUFFER_SIZE];
							while(br.read(buf) != -1)
								;
						}
						catch(IOException e) {
							e.printStackTrace();
						}
					}
				}
			);
	}

	private static void deleteFiles(ExecutorService es)
	{
		_fileCount.set(1);
		for(int i = 0; i < NUMBER_OF_FILES; i++)
			es.execute(
				new Runnable() {
					@Override
					public void run()
					{
						try {
							Files.delete(
								Paths.get(
									TARGET_DIR.toString(),
									String.format(FILENAMEPATTERN, _fileCount.getAndIncrement())
								)
							);
						}
						catch(IOException e) {
							e.printStackTrace();
						}
					}
				}
			);
	}

	private static void print(String str, int d)
	{
		Console console = System.console();
		if(console != null) {
			String back = "";
			for(int i = 0; i < str.length() + 2; i++)
				back += "\u0008";
			console.printf(back + str + "%2d", d);
		}
		else
			System.out.println(str + d);
	}

	private static int sumOfFactorsInRange(final int lower, final int upper, final int number)
	{
		int sum = 0;
		for(int i = lower; i <= upper; i++)
			if(number % i == 0)
				sum += i;
		return sum;
	}

	private static long countRange(int lower, int upper, Perfectum p)
	{
		final long start = System.currentTimeMillis();
		for(int i = lower; i <= upper; i++)
			p.isPerfect(i);
		return System.currentTimeMillis() - start;
	}

	interface Perfectum
	{
		boolean isPerfect(final int candidate);
	}

	static class IsPerfect implements Perfectum
	{
		@Override
		public boolean isPerfect(final int candidate)
		{
			return 2 * candidate == sumOfFactorsInRange(1, candidate, candidate);
		}
	}

	static class IsPerfectConcurrent implements Perfectum
	{
		private final ExecutorService _es;
		public IsPerfectConcurrent(ExecutorService es)
		{
			_es = es;
		}
		@Override
		public boolean isPerfect(final int candidate)
		{
			final int numOfPartitions = (int)Math.ceil(candidate / (double)RANGE);
			List<Future<Integer>> l = new LinkedList<>();
			for(int count = 0; count < numOfPartitions; count++) {
				final int i = count;
				Callable<Integer> c =
					new Callable<Integer>()
					{
						@Override
						public Integer call() throws Exception
						{
							final int lower = i * RANGE + 1;
							final int upper = Math.min(candidate, (i + 1) * RANGE);
							return sumOfFactorsInRange(lower, upper, candidate);
						}
					};
				l.add(_es.submit(c));
			}
			int sum = 0;
			try {
				for(Future<Integer> i : l)
					sum += i.get();
			} catch (Exception e) {
				e.printStackTrace();
			}
			return 2 * candidate == sum;
		}
	}

	public static class Cases
	{
		private int _numOfProcessors;
		private int _numOfCases;
		private int[] _numOfThreads;
		private long[] _cpu;
		private long[] _fileWrite;
		private long[] _fileRead;
		private long[] _fileDelete;
		private long[] _totalTimes;

		public Cases(int numOfProcessors, List<Integer> numOfThreads)
		{
			init(numOfProcessors, numOfThreads);
		}
		public Cases(int numOfProcessors, int lowerNumOfThreads, int upperNumOfThreads)
		{
			final List<Integer> numOfThreads = new ArrayList<>();
			for(int i = lowerNumOfThreads; i <= upperNumOfThreads; i++)
				numOfThreads.add(i);
			init(numOfProcessors, numOfThreads);
		}
		public Cases(int numOfProcessors, int oneCaseNumOfThreas)
		{
			this(numOfProcessors, oneCaseNumOfThreas, oneCaseNumOfThreas);
		}

		private void init(int numOfProcessors, List<Integer> numOfThreads)
		{
			_numOfProcessors = numOfProcessors;
			_numOfCases = numOfThreads.size();
			_numOfThreads = new int[_numOfCases];
			for(int i = 0; i < _numOfCases; i++)
				_numOfThreads[i] = numOfThreads.get(i);
			_cpu = new long[_numOfCases];
			_fileWrite = new long[_numOfCases];
			_fileRead = new long[_numOfCases];
			_fileDelete = new long[_numOfCases];
			_totalTimes = new long[_numOfCases];
		}

		public int[] numOfThreads()
		{
			return _numOfThreads;
		}

		public void setCpuTime(int caseIndex, long time)
		{
			_cpu[caseIndex] = time;
		}

		public void setFileWriteTime(int caseIndex, long time)
		{
			_fileWrite[caseIndex] = time;
		}

		public void setFileReadTime(int caseIndex, long time)
		{
			_fileRead[caseIndex] = time;
		}

		public void setFileDeleteTime(int caseIndex, long time)
		{
			_fileDelete[caseIndex] = time;
		}

		public void printResults()
		{
			long totalTime = 0;
			for(int i = 0; i < _numOfCases; i++) {
				_totalTimes[i] = _cpu[i] + _fileWrite[i] + _fileRead[i] + _fileDelete[i];
				totalTime += _totalTimes[i];
			}
			System.out.println(String.format("Total test time: %4.3f s", totalTime / 1000.0));
			System.out.println(
				String.format(
					"%-6s%-8s%11s%10s%10s%10s%10s",
					" Case",
					" Num of",
					"Total ",
					"CPU  ",
					"File W ",
					"File R ",
					"File D "
				)
			);
			System.out.println(
				String.format(
					"%-6s%-8s%11s%10s%10s%10s%10s",
					"   #",
					" threads",
					"(s)  ",
					"(s)  ",
					"(s)  ",
					"(s)  ",
					"(s)  "
				)
			);
			int caseIndex = 0;
			for(int numOfThread : numOfThreads()) {
				System.out.println(
					String.format(
						"%1s %2d.    %3d  %11.3f%10.3f%10.3f%10.3f%10.3f",
						_numOfProcessors == numOfThread ? "*" : "",
						caseIndex + 1,
						numOfThread,
						_totalTimes[caseIndex] / 1000.0,
						_cpu[caseIndex] / 1000.0,
						_fileWrite[caseIndex] / 1000.0,
						_fileRead[caseIndex] / 1000.0,
						_fileDelete[caseIndex] / 1000.0
					)
				);
				caseIndex++;
			}
		}
	}
}
