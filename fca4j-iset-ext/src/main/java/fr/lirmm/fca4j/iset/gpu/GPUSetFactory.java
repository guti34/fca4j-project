package fr.lirmm.fca4j.iset.gpu;

import static org.jocl.CL.*;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;

import org.jocl.CL;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_context_properties;
import org.jocl.cl_device_id;
import org.jocl.cl_kernel;
import org.jocl.cl_mem;
import org.jocl.cl_platform_id;
import org.jocl.cl_program;
import org.jocl.cl_queue_properties;

import fr.lirmm.fca4j.iset.AbstractSetFactory;
import fr.lirmm.fca4j.iset.ISet;

/**
 *
 * @author agutierr
 */
public class GPUSetFactory extends AbstractSetFactory {

	protected cl_context context;
	protected cl_command_queue commandQueue;
	protected cl_program program;
	protected cl_kernel kernelAND;
	protected cl_kernel kernelNAND;
	protected cl_kernel kernelOR;
	protected cl_kernel kernelAND3;
	protected cl_kernel kernelOR3;
	protected cl_kernel kernelNAND3;
	protected cl_kernel kernelCARD;
	protected cl_kernel kernelCONTAINS;
	protected cl_kernel kernelFILL;
	protected cl_kernel kernelCLEAR;
	protected cl_kernel kernelEQUALS;
	protected cl_kernel kernelCOMPUTE_INTENT;
	protected cl_kernel kernelEXOFLACK;

	public GPUSetFactory() {
		// The platform, device type and device number
		// that will be used
		final int platformIndex = 0;
		final long deviceType = CL_DEVICE_TYPE_GPU;
		final int deviceIndex = 0;
		// Enable exceptions and subsequently omit error checks in this sample
		CL.setExceptionsEnabled(true);

		// Obtain the number of platforms
		int numPlatformsArray[] = new int[1];
		clGetPlatformIDs(0, null, numPlatformsArray);
		int numPlatforms = numPlatformsArray[0];

		// Obtain a platform ID
		cl_platform_id platforms[] = new cl_platform_id[numPlatforms];
		clGetPlatformIDs(platforms.length, platforms, null);
		cl_platform_id platform = platforms[platformIndex];

		// Initialize the context properties
		cl_context_properties contextProperties = new cl_context_properties();
		contextProperties.addProperty(CL_CONTEXT_PLATFORM, platform);

		// Obtain the number of devices for the platform
		int numDevicesArray[] = new int[1];
		clGetDeviceIDs(platform, deviceType, 0, null, numDevicesArray);
		int numDevices = numDevicesArray[0];

		// Obtain a device ID
		cl_device_id devices[] = new cl_device_id[numDevices];
		clGetDeviceIDs(platform, deviceType, numDevices, devices, null);
		cl_device_id device = devices[deviceIndex];
		// Create a context for the selected device
		context = clCreateContext(contextProperties, 1, new cl_device_id[] { device }, null, null, null);

		// Create a command-queue for the selected device
		cl_queue_properties properties = new cl_queue_properties();
		commandQueue = clCreateCommandQueueWithProperties(context, device, properties, null);
		// Create the program from the source code
		String programSource = readProgramSource();
		program = clCreateProgramWithSource(context, 1, new String[] { programSource }, null, null);

		// Build the program
		clBuildProgram(program, 0, null, null, null, null);

		// Create the kernels
		kernelAND = clCreateKernel(program, "and", null);
		kernelNAND = clCreateKernel(program, "nand", null);
		kernelOR = clCreateKernel(program, "or", null);
		kernelAND3 = clCreateKernel(program, "and3", null);
		kernelNAND3 = clCreateKernel(program, "nand3", null);
		kernelOR3 = clCreateKernel(program, "or3", null);
		kernelCARD = clCreateKernel(program, "cardinality", null);
		kernelCONTAINS = clCreateKernel(program, "contains", null);
		kernelCLEAR = clCreateKernel(program, "clear", null);
		kernelFILL = clCreateKernel(program, "fill", null);
		kernelEQUALS = clCreateKernel(program, "equals", null);
		kernelCOMPUTE_INTENT = clCreateKernel(program, "computeIntent", null);
		kernelEXOFLACK = clCreateKernel(program, "extensionOfLack", null);
	}

	public cl_mem createBufferMatrix(List<ISet> intents, int nbAttr) {
		// populate matrix
		int[][] matrix = new int[intents.size()][nbAttr];
		for (int numobj = 0; numobj < intents.size(); numobj++) {
			ISet intent = intents.get(numobj);
			for (Iterator<Integer> it = intent.iterator(); it.hasNext();) {
				int numattr = it.next();
				matrix[numobj][numattr] = 1;
			}
		}
		int matrix_size = intents.size() * nbAttr;
		cl_mem mem_data = clCreateBuffer(context, CL_MEM_READ_ONLY , Sizeof.cl_int * matrix_size,
				null, null);
		// Write the source array into the buffer
		writeBuffer2D(mem_data, matrix);
		return mem_data;
	}

	public ISet extensionOfLack(cl_mem mem_data, int nbObj, int nbAttr, ISet extent, ISet zeros) {
		SetWithGPUBoolArray result = (SetWithGPUBoolArray) createSet(nbAttr);
//		SetWithGPUBoolArray work = (SetWithGPUBoolArray) createSet(nbAttr);
		clSetKernelArg(kernelEXOFLACK, 0, Sizeof.cl_mem, Pointer.to(mem_data));
		clSetKernelArg(kernelEXOFLACK, 1, Sizeof.cl_mem, Pointer.to(((SetWithGPUBoolArray) extent).mem_array));
		clSetKernelArg(kernelEXOFLACK, 2, Sizeof.cl_mem, Pointer.to(((SetWithGPUBoolArray) zeros).mem_array));
//		clSetKernelArg(kernelEXOFLACK, 3, Sizeof.cl_mem, Pointer.to(((SetWithGPUBoolArray) work).mem_array));
		clSetKernelArg(kernelEXOFLACK, 3, Sizeof.cl_int * nbAttr, null);
		// arg4 change
		
		clSetKernelArg(kernelEXOFLACK, 5, Sizeof.cl_int, Pointer.to(new int[] { nbAttr }));
		clSetKernelArg(kernelEXOFLACK, 6, Sizeof.cl_mem, Pointer.to(result.mem_array));
		for (int numattr = 0; numattr < nbAttr; numattr++) {
			// Execute the kernel
			clSetKernelArg(kernelEXOFLACK, 4, Sizeof.cl_int, Pointer.to(new int[] {numattr}));
			try {
			clEnqueueNDRangeKernel(commandQueue, kernelEXOFLACK, 2, null, new long[] { nbObj, nbAttr }, null, 0, null,
					null);
			}catch(Exception e) {
				System.out.println("attr="+numattr);
				throw e;
			}
		}
		// Read the buffer back to the array
		clEnqueueReadBuffer(commandQueue, result.mem_array, CL_TRUE, 0, nbAttr * Sizeof.cl_int,
				Pointer.to(result.array), 0, null, null);
		result.gpuDirty = false;
		result.hostDirty = false;
		return result;
	}

	public ISet computeIntent(cl_mem mem_data, int nbObj, int nbAttr, ISet extent, ISet result) {
		// set args
		clSetKernelArg(kernelCOMPUTE_INTENT, 0, Sizeof.cl_mem, Pointer.to(mem_data));
		clSetKernelArg(kernelCOMPUTE_INTENT, 1, Sizeof.cl_mem, Pointer.to(((SetWithGPUBoolArray)extent).mem_array));
		clSetKernelArg(kernelCOMPUTE_INTENT, 2, Sizeof.cl_int, Pointer.to(new int[] { nbAttr }));
		clSetKernelArg(kernelCOMPUTE_INTENT, 3, Sizeof.cl_mem, Pointer.to(((SetWithGPUBoolArray)result).mem_array));

		// Execute the kernel
		clEnqueueNDRangeKernel(commandQueue, kernelCOMPUTE_INTENT, 2, null, new long[] { nbObj, nbAttr }, null, 0, null,
				null);
		// Read the buffer back to the array
		clEnqueueReadBuffer(commandQueue, ((SetWithGPUBoolArray)result).mem_array, CL_TRUE, 0, nbAttr * Sizeof.cl_int,
				Pointer.to(((SetWithGPUBoolArray)result).array), 0, null, null);
		((SetWithGPUBoolArray)result).gpuDirty = false;
		((SetWithGPUBoolArray)result).hostDirty = false;
//		System.out.println(result);
		return result;
	}

	private void writeBuffer2D(cl_mem buffer, int array[][]) {
		long byteOffset = 0;
		for (int r = 0; r < array.length; r++) {
			int bytes = array[r].length * Sizeof.cl_int;
			clEnqueueWriteBuffer(commandQueue, buffer, CL_TRUE, byteOffset, bytes, Pointer.to(array[r]), 0, null, null);
			byteOffset += bytes;
		}
	}

	private void readBuffer2D(cl_mem buffer, int array[][]) {
		long byteOffset = 0;
		for (int r = 0; r < array.length; r++) {
			int bytes = array[r].length * Sizeof.cl_int;
			clEnqueueReadBuffer(commandQueue, buffer, CL_TRUE, byteOffset, bytes, Pointer.to(array[r]), 0, null, null);
			byteOffset += bytes;
		}
	}

	@Override
	public ISet createSet() {
		throw new UnsupportedOperationException("this kind of set has a fixed size");
	}

	@Override
	public ISet createSet(BitSet bs) {
		return new SetWithGPUBoolArray((BitSet) bs.clone());
	}

	@Override
	public ISet createSet(BitSet bs, int size) {
		return new SetWithGPUBoolArray(bs, size);
	}

	@Override
	public ISet createSet(int initialCapacity) {
		return new SetWithGPUBoolArray(initialCapacity);
	}

	@Override
	public boolean ordered() {
		return true;
	}

	@Override
	public boolean fixedSize() {
		return true;
	}

	@Override
	public String name() {
		return "OPENCL_GPU";
	}

	@Override
	public ISet clone(ISet b) {
		SetWithGPUBoolArray b2 = (SetWithGPUBoolArray) b;
		if (b2.hostDirty)
			b2.syncHost();
		int[] newArray = ((SetWithGPUBoolArray) b).array.clone();
		SetWithGPUBoolArray clonedSet = new SetWithGPUBoolArray(newArray);
		return clonedSet;
	}

	private String readProgramSource() {
		String path = "/gpu_program.cl";
		String program = null;
		try {
			InputStream inputStream = getClass().getResourceAsStream(path);
			StringBuilder textBuilder = new StringBuilder();
			try (Reader reader = new BufferedReader(
					new InputStreamReader(inputStream, Charset.forName(StandardCharsets.UTF_8.name())))) {
				int c = 0;
				while ((c = reader.read()) != -1) {
					textBuilder.append((char) c);
				}
			}
			program = textBuilder.toString();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return program;
	}

	@Override
	public void finalize() {
		clReleaseKernel(kernelAND);
		clReleaseKernel(kernelNAND);
		clReleaseKernel(kernelOR);
		clReleaseKernel(kernelAND3);
		clReleaseKernel(kernelNAND3);
		clReleaseKernel(kernelOR3);
		clReleaseKernel(kernelCARD);
		clReleaseKernel(kernelCONTAINS);
		clReleaseKernel(kernelFILL);
		clReleaseKernel(kernelCLEAR);
		clReleaseKernel(kernelEQUALS);
		clReleaseKernel(kernelCOMPUTE_INTENT);
		clReleaseKernel(kernelEXOFLACK);
		clReleaseProgram(program);
		clReleaseCommandQueue(commandQueue);
		clReleaseContext(context);
	}

	/**
	 * GPU ISet
	 * 
	 * @author gutierre
	 *
	 */
	class SetWithGPUBoolArray extends AbstractOrderedSet {

		private int localWorkSize = 128;
		private int numWorkGroups = 64;
		private boolean gpuDirty = true;
		private boolean hostDirty = false;
		private int[] array;
		private int outputArray[] = new int[numWorkGroups];
		private cl_mem mem_array;
		private cl_mem mem_output;

		SetWithGPUBoolArray(int size) {
			array = new int[size];
			initBuffers();
		}

		SetWithGPUBoolArray(int[] array) {
			this.array = array;
			initBuffers();
		}

		SetWithGPUBoolArray(BitSet bitSet) {
			this(bitSet, bitSet.size());
		}

		SetWithGPUBoolArray(BitSet bitSet, int size) {
			array = new int[size];
			for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
				add(i);
			}
			initBuffers();
		}

		private void initBuffers() {
			mem_array = clCreateBuffer(context, CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR, Sizeof.cl_int * array.length,
					Pointer.to(array), null);
			mem_output = clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, Sizeof.cl_int * numWorkGroups,
					Pointer.to(outputArray), null);
		}

		public boolean getHostDirty() {
			return hostDirty;
		}

		public boolean getGPUDirty() {
			return gpuDirty;
		}

		protected void syncHost() {
//			System.out.println("syncHost");
			// Read the output data
			clEnqueueReadBuffer(commandQueue, mem_array, CL_TRUE, 0, array.length * Sizeof.cl_int, Pointer.to(array), 0,
					null, null);
			hostDirty = false;
		}

		protected void syncGPU() {
//			System.out.println("syncGPU");
			clEnqueueWriteBuffer(commandQueue, mem_array, CL_TRUE, 0, array.length * Sizeof.cl_int, Pointer.to(array),
					0, null, null);
			gpuDirty = false;
		}

		@Override
		public void add(int num) {
			if (hostDirty)
				syncHost();
			array[num] = 1;
			gpuDirty = true;
		}

		@Override
		public void addAll(ISet anotherSet) {
			if (gpuDirty)
				syncGPU();
			if (((SetWithGPUBoolArray) anotherSet).gpuDirty)
				((SetWithGPUBoolArray) anotherSet).syncGPU();
			executeKernel(kernelOR, mem_array, ((SetWithGPUBoolArray) anotherSet).mem_array);
			hostDirty = true;
		}

		@Override
		public boolean contains(int num) {
			if (hostDirty)
				syncHost();
			return array[num] != 0;
		}

		@Override
		public int capacity() {
			return array.length;
		}

		@Override
		public void fill(int size) {
			if (gpuDirty)
				syncGPU();
			clSetKernelArg(kernelFILL, 0, Sizeof.cl_mem, Pointer.to(mem_array));
			clSetKernelArg(kernelFILL, 1, Sizeof.cl_int, Pointer.to(new int[] { size }));
			// Set the work-item dimensions
			long global_work_size[] = new long[] { array.length };

			// Execute the kernel
			clEnqueueNDRangeKernel(commandQueue, kernelFILL, 1, null, global_work_size, null, 0, null, null);
			hostDirty = true;
		}

		@Override
		public void clear(int size) {
			if (gpuDirty)
				syncGPU();
			clSetKernelArg(kernelCLEAR, 0, Sizeof.cl_mem, Pointer.to(mem_array));
			clSetKernelArg(kernelCLEAR, 1, Sizeof.cl_int, Pointer.to(new int[] { size }));
			// Set the work-item dimensions
			long global_work_size[] = new long[] { array.length };

			// Execute the kernel
			clEnqueueNDRangeKernel(commandQueue, kernelCLEAR, 1, null, global_work_size, null, 0, null, null);
			hostDirty = true;
		}

		@Override
		public void removeAll(ISet anotherSet) {
			if (gpuDirty)
				syncGPU();
			if (((SetWithGPUBoolArray) anotherSet).gpuDirty)
				((SetWithGPUBoolArray) anotherSet).syncGPU();
			executeKernel(kernelNAND, mem_array, ((SetWithGPUBoolArray) anotherSet).mem_array);
			hostDirty = true;
		}

		@Override
		public Iterator<Integer> iterator() {
			if (hostDirty)
				syncHost();
			return new Iterator<Integer>() {
				int counter = 0;
				int last = last();

				@Override
				public boolean hasNext() {
					return counter <= last;
				}

				@Override
				public Integer next() {
					for (int i = counter;; i++)
						if (array[i] == 1) {
							counter = i + 1;
							return i;
						}
				}
			};
		}

		@Override
		public boolean isEmpty() {
			return cardinality() == 0;
		}

		public boolean isEmpty2() {
			if (hostDirty)
				syncHost();
			for (int i = 0; i < array.length; i++)
				if (array[i] != 0)
					return false;
			return true;
		}

		@Override
		public ISet newIntersect(ISet anotherSet) {
			if (gpuDirty)
				syncGPU();
			if (((SetWithGPUBoolArray) anotherSet).gpuDirty)
				((SetWithGPUBoolArray) anotherSet).syncGPU();
			SetWithGPUBoolArray result = new SetWithGPUBoolArray(array.length);
			executeKernel3(kernelAND3, mem_array, ((SetWithGPUBoolArray) anotherSet).mem_array, result.mem_array);
			result.hostDirty = true;
			result.gpuDirty = false;
			return result;
		}

		@Override
		public ISet newDifference(ISet anotherSet) {
			if (gpuDirty)
				syncGPU();
			if (((SetWithGPUBoolArray) anotherSet).gpuDirty)
				((SetWithGPUBoolArray) anotherSet).syncGPU();
			SetWithGPUBoolArray result = new SetWithGPUBoolArray(array.length);
			executeKernel3(kernelNAND3, mem_array, ((SetWithGPUBoolArray) anotherSet).mem_array, result.mem_array);
			result.hostDirty = true;
			result.gpuDirty = false;
			return result;
		}

		@Override
		public void remove(int num) {
			if (hostDirty)
				syncHost();
			array[num] = 0;
			gpuDirty = true;
		}

		@Override
		public void retainAll(ISet anotherSet) {
			if (gpuDirty)
				syncGPU();
			if (((SetWithGPUBoolArray) anotherSet).gpuDirty)
				((SetWithGPUBoolArray) anotherSet).syncGPU();
			executeKernel(kernelAND, mem_array, ((SetWithGPUBoolArray) anotherSet).mem_array);
			hostDirty = true;
		}

		@Override
		public int first() {
			if (hostDirty)
				syncHost();
			for (int i = 0; i < array.length; i++)
				if (array[i] != 0)
					return i;
			return -1;
		}

		@Override
		public int last() {
			if (hostDirty)
				syncHost();
			for (int i = array.length - 1; i >= 0; i--)
				if (array[i] != 0)
					return i;
			return -1;
		}

		protected boolean executeKernel(cl_kernel kernel, cl_mem a, cl_mem c) {
			clSetKernelArg(kernel, 0, Sizeof.cl_mem, Pointer.to(a));
			clSetKernelArg(kernel, 1, Sizeof.cl_mem, Pointer.to(c));
			// Set the work-item dimensions
			long global_work_size[] = new long[] { array.length };

			// Execute the kernel
			clEnqueueNDRangeKernel(commandQueue, kernel, 1, null, global_work_size, null, 0, null, null);
			return true;
		}

		protected boolean executeKernel3(cl_kernel kernel, cl_mem a, cl_mem b, cl_mem c) {
			clSetKernelArg(kernel, 0, Sizeof.cl_mem, Pointer.to(a));
			clSetKernelArg(kernel, 1, Sizeof.cl_mem, Pointer.to(b));
			clSetKernelArg(kernel, 2, Sizeof.cl_mem, Pointer.to(c));
			// Set the work-item dimensions
			long global_work_size[] = new long[] { array.length };

			// Execute the kernel
			clEnqueueNDRangeKernel(commandQueue, kernel, 1, null, global_work_size, null, 0, null, null);
			return true;
		}

		public int cardinality2() {
			if (hostDirty)
				syncHost();
			int count = 0;
			for (int i = 0; i < array.length; i++)
				if (array[i] != 0)
					count++;
			return count;
		}

		@Override
		public int cardinality() {
			if (gpuDirty)
				syncGPU();

			reduce1(kernelCARD, mem_array, array.length, mem_output, numWorkGroups, localWorkSize);
			clEnqueueReadBuffer(commandQueue, mem_output, CL_TRUE, 0, outputArray.length * Sizeof.cl_int,
					Pointer.to(outputArray), 0, null, null);
			int result = reduceHost(outputArray);
			return result;
		}

		@Override
		public boolean containsAll(ISet another) {
			if (gpuDirty)
				syncGPU();
			if (((SetWithGPUBoolArray) another).gpuDirty)
				syncGPU();
			reduce2(kernelCONTAINS, mem_array, ((SetWithGPUBoolArray) another).mem_array, array.length, mem_output,
					numWorkGroups, localWorkSize);

			clEnqueueReadBuffer(commandQueue, mem_output, CL_TRUE, 0, outputArray.length * Sizeof.cl_int,
					Pointer.to(outputArray), 0, null, null);
			int result = reduceHost(outputArray);
			return result == 0;
		}

		public boolean containsAll2(ISet anotherSet) {
			return anotherSet.newIntersect(this).cardinality() == anotherSet.cardinality();
		}

		public boolean containsAll3(ISet anotherSet) {
			return anotherSet.newDifference(this).isEmpty();
		}

		/**
		 * Perform a reduction of the float elements in the given input memory. Each
		 * work group will reduce 'localWorkSize' elements, and write the result into
		 * the given output memory.
		 * 
		 * @param inputMem      The input memory containing the float values to reduce
		 * @param n             The number of values in the input memory
		 * @param outputMem     The output memory that will store the reduction result
		 *                      for each work group
		 * @param numWorkGroups The number of work groups
		 * @param localWorkSize The local work size, that is, the number of work items
		 *                      in each work group
		 */
		private void reduce1(cl_kernel kernel, cl_mem inputMem, int n, cl_mem outputMem, int numWorkGroups,
				int localWorkSize) {
			// Set the arguments for the kernel
			int a = 0;
			clSetKernelArg(kernel, a++, Sizeof.cl_mem, Pointer.to(inputMem));
			clSetKernelArg(kernel, a++, Sizeof.cl_int * localWorkSize, null);
			clSetKernelArg(kernel, a++, Sizeof.cl_int, Pointer.to(new int[] { n }));
			clSetKernelArg(kernel, a++, Sizeof.cl_mem, Pointer.to(outputMem));

			// Compute the number of work groups and the global work size
			long globalWorkSize = numWorkGroups * localWorkSize;

			// Execute the kernel
			clEnqueueNDRangeKernel(commandQueue, kernel, 1, null, new long[] { globalWorkSize },
					new long[] { localWorkSize }, 0, null, null);
		}

		private void reduce2(cl_kernel kernel, cl_mem inputMem1, cl_mem inputMem2, int n, cl_mem outputMem,
				int numWorkGroups, int localWorkSize) {
			// Set the arguments for the kernel
			int a = 0;
			clSetKernelArg(kernel, a++, Sizeof.cl_mem, Pointer.to(inputMem1));
			clSetKernelArg(kernel, a++, Sizeof.cl_mem, Pointer.to(inputMem2));
			clSetKernelArg(kernel, a++, Sizeof.cl_int * localWorkSize, null);
			clSetKernelArg(kernel, a++, Sizeof.cl_int, Pointer.to(new int[] { n }));
			clSetKernelArg(kernel, a++, Sizeof.cl_mem, Pointer.to(outputMem));

			// Compute the number of work groups and the global work size
			long globalWorkSize = numWorkGroups * localWorkSize;

			// Execute the kernel
			clEnqueueNDRangeKernel(commandQueue, kernel, 1, null, new long[] { globalWorkSize },
					new long[] { localWorkSize }, 0, null, null);
		}

		private int reduceHost(int tab[]) {
//	    	System.out.println("reduceHost "+toString(tab));
//        	System.out.println("array="+this);
			int sum = 0;
			for (int i = 0; i < tab.length; i++)
				sum += tab[i];
			return sum;
		}

		/*
		 * public String toString(int[] tab) { String s = null; for (int i = 0; i <
		 * tab.length; i++) { if (s == null) { s = "[" + tab[i]; } else { s += "," +
		 * tab[i]; } } return s == null ? "[]" : s + "]"; }
		 */
		@Override
		public int hashCode() {
			return array.hashCode();
		}

		@Override
		public boolean equals(Object other) {
			if (gpuDirty)
				syncGPU();
			if (((SetWithGPUBoolArray) other).gpuDirty)
				syncGPU();
			reduce2(kernelEQUALS, mem_array, ((SetWithGPUBoolArray) other).mem_array, array.length, mem_output,
					numWorkGroups, localWorkSize);

			clEnqueueReadBuffer(commandQueue, mem_output, CL_TRUE, 0, outputArray.length * Sizeof.cl_int,
					Pointer.to(outputArray), 0, null, null);
			int result = reduceHost(outputArray);
			return result == 0;
		}

		public boolean equals2(Object other) {
			return containsAll((ISet) other) && ((ISet) other).containsAll(this);
//			SetWithGPUBoolArray other2=(SetWithGPUBoolArray)other;
//				return cardinality()==other2.cardinality() && containsAll(other2);
		}

		@Override
		public void finalize() {
			clReleaseMemObject(mem_array);
			clReleaseMemObject(mem_output);
		}
	}

}
