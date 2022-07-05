package fr.lirmm.fca4j.iset.gpu;

import static org.jocl.CL.CL_CONTEXT_PLATFORM;
import static org.jocl.CL.CL_DEVICE_TYPE_GPU;
import static org.jocl.CL.CL_TRUE;
import static org.jocl.CL.clBuildProgram;
import static org.jocl.CL.clCreateBuffer;
import static org.jocl.CL.clCreateCommandQueueWithProperties;
import static org.jocl.CL.clCreateContext;
import static org.jocl.CL.clCreateKernel;
import static org.jocl.CL.clCreateProgramWithSource;
import static org.jocl.CL.clEnqueueReadBuffer;
import static org.jocl.CL.clEnqueueWriteBuffer;
import static org.jocl.CL.clGetDeviceIDs;
import static org.jocl.CL.clGetPlatformIDs;
import static org.jocl.CL.clReleaseCommandQueue;
import static org.jocl.CL.clReleaseContext;
import static org.jocl.CL.clReleaseKernel;
import static org.jocl.CL.clReleaseProgram;

import java.util.HashMap;
import java.util.HashSet;
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

import fr.lirmm.fca4j.iset.ISet;

public class OpenCLContext {
	protected cl_context context;
	protected cl_command_queue commandQueue;
	protected cl_program program;
	protected HashMap<String,cl_kernel> kernels;
	public OpenCLContext(String programSource){
		// The platform, device type and device number
		// that will be used
		final int platformIndex = 0;
		final long deviceType = CL_DEVICE_TYPE_GPU;
		final int deviceIndex = 0;
		// Enable exceptions and subsequently omit error checks
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
		program = clCreateProgramWithSource(context, 1, new String[] { programSource }, null, null);

		// Build the program
		clBuildProgram(program, 0, null, null, null, null);
		
	}
	public void createKernel(String kernel_name) {
		cl_kernel kernel=clCreateKernel(program, kernel_name, null);
		kernels.put(kernel_name,kernel);
	}
	public cl_mem createMatrixBuffer(List<ISet> intents, int nbAttr,long flags) {
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
		cl_mem mem_data = clCreateBuffer(context, flags , Sizeof.cl_int * matrix_size,
				null, null);
		// Write the source array into the buffer
		writeBuffer2D(mem_data, matrix);
		return mem_data;
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
	public void finalize() {
		for(cl_kernel kernel:kernels.values()) {
			clReleaseKernel(kernel);
		}
		clReleaseProgram(program);
		clReleaseCommandQueue(commandQueue);
		clReleaseContext(context);
	}
}
