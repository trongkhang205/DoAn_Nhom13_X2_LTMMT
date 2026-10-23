using System;
using System.IO;
using System.Runtime.InteropServices;
using System.Threading;

namespace AudioLoopback
{
    class Program
    {
        [ComImport, Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")]
        class MMDeviceEnumeratorComObject { }

        [Guid("A95664D2-9614-4F35-A746-DE8DB63617E6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        interface IMMDeviceEnumerator
        {
            int EnumAudioEndpoints(int dataFlow, int stateMask, out IntPtr devices);
            int GetDefaultAudioEndpoint(int dataFlow, int role, out IMMDevice endpoint);
            int GetDevice(string id, out IMMDevice device);
            int RegisterEndpointNotificationCallback(IntPtr client);
            int UnregisterEndpointNotificationCallback(IntPtr client);
        }

        [Guid("D666063F-1587-4E43-81F1-B948E807363F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        interface IMMDevice
        {
            int Activate(ref Guid id, int clsCtx, IntPtr activationParams, [MarshalAs(UnmanagedType.IUnknown)] out object interfacePointer);
            int OpenPropertyStore(int stgmAccess, out IntPtr properties);
            int GetId(out IntPtr id);
            int GetState(out int state);
        }

        [Guid("1CB9AD4C-DBFA-4c32-B178-C2F568A703B2"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        interface IAudioClient
        {
            int Initialize(int shareMode, int streamFlags, long hnsBufferDuration, long hnsPeriodicity, IntPtr pFormat, ref Guid audioSessionGuid);
            int GetBufferSize(out uint numBufferFrames);
            int GetStreamLatency(out long hnsLatency);
            int GetCurrentPadding(out uint numPaddingFrames);
            int IsFormatSupported(int shareMode, IntPtr pFormat, out IntPtr ppClosestMatch);
            int GetMixFormat(out IntPtr ppDeviceFormat);
            int GetDevicePeriod(out long hnsDefaultDevicePeriod, out long hnsMinimumDevicePeriod);
            int Start();
            int Stop();
            int Reset();
            int SetEventHandle(IntPtr eventHandle);
            int GetService(ref Guid riid, [MarshalAs(UnmanagedType.IUnknown)] out object ppv);
        }

        [Guid("C8ADBD64-E71E-48a0-A4DE-185C395CD317"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        interface IAudioCaptureClient
        {
            int GetBuffer(out IntPtr dataPointer, out uint numFramesToRead, out int flags, out ulong devicePosition, out ulong qpcPosition);
            int ReleaseBuffer(uint numFramesRead);
            int GetNextPacketSize(out uint numFramesInNextPacket);
        }

        [StructLayout(LayoutKind.Sequential)]
        struct WAVEFORMATEX
        {
            public ushort wFormatTag;
            public ushort nChannels;
            public uint nSamplesPerSec;
            public uint nAvgBytesPerSec;
            public ushort nBlockAlign;
            public ushort wBitsPerSample;
            public ushort cbSize;
        }

        static void Main(string[] args)
        {
            try
            {
                var enumerator = (IMMDeviceEnumerator)new MMDeviceEnumeratorComObject();
                IMMDevice device;
                // eRender = 0, eConsole = 0
                int hr = enumerator.GetDefaultAudioEndpoint(0, 0, out device);
                if (hr != 0 || device == null)
                {
                    Console.Error.WriteLine("ERR: Cannot get default render endpoint: " + hr);
                    return;
                }

                Guid IID_IAudioClient = new Guid("1CB9AD4C-DBFA-4c32-B178-C2F568A703B2");
                object clientObj;
                hr = device.Activate(ref IID_IAudioClient, 1 /* CLSCTX_ALL */, IntPtr.Zero, out clientObj);
                if (hr != 0 || clientObj == null)
                {
                    Console.Error.WriteLine("ERR: Cannot activate IAudioClient: " + hr);
                    return;
                }

                IAudioClient audioClient = (IAudioClient)clientObj;
                IntPtr pFormat;
                audioClient.GetMixFormat(out pFormat);

                WAVEFORMATEX format = (WAVEFORMATEX)Marshal.PtrToStructure(pFormat, typeof(WAVEFORMATEX));
                uint sampleRate = format.nSamplesPerSec;
                int channels = format.nChannels;
                bool isFloat = (format.wFormatTag == 3) || (format.wBitsPerSample == 32);

                // Print header for Java host
                Console.Error.WriteLine("WASAPI_READY|RATE=" + sampleRate + "|CHANNELS=" + channels + "|FLOAT=" + isFloat);

                Guid sessionGuid = Guid.Empty;
                // AUDCLNT_SHAREMODE_SHARED = 0, AUDCLNT_STREAMFLAGS_LOOPBACK = 0x00020000
                hr = audioClient.Initialize(0, 0x00020000, 10000000 /* 1 sec buffer */, 0, pFormat, ref sessionGuid);
                if (hr != 0)
                {
                    Console.Error.WriteLine("ERR: audioClient.Initialize failed: " + hr);
                    return;
                }

                Guid IID_IAudioCaptureClient = new Guid("C8ADBD64-E71E-48a0-A4DE-185C395CD317");
                object captureObj;
                hr = audioClient.GetService(ref IID_IAudioCaptureClient, out captureObj);
                if (hr != 0 || captureObj == null)
                {
                    Console.Error.WriteLine("ERR: audioClient.GetService failed: " + hr);
                    return;
                }

                IAudioCaptureClient captureClient = (IAudioCaptureClient)captureObj;
                audioClient.Start();

                Stream stdout = Console.OpenStandardOutput();
                byte[] outBuffer = new byte[7680]; // ~40ms chunk at 48kHz 16-bit mono
                int outPos = 0;

                while (true)
                {
                    uint packetLength;
                    hr = captureClient.GetNextPacketSize(out packetLength);
                    if (hr != 0) break;

                    while (packetLength > 0)
                    {
                        IntPtr pData;
                        uint numFrames;
                        int flags;
                        ulong devPos, qpcPos;
                        hr = captureClient.GetBuffer(out pData, out numFrames, out flags, out devPos, out qpcPos);
                        if (hr != 0) break;

                        if (numFrames > 0)
                        {
                            bool silent = (flags & 1) != 0; // AUDCLNT_BUFFERFLAGS_SILENT

                            if (silent)
                            {
                                int numBytes = (int)numFrames * 2;
                                while (numBytes > 0)
                                {
                                    int toWrite = Math.Min(numBytes, outBuffer.Length - outPos);
                                    Array.Clear(outBuffer, outPos, toWrite);
                                    outPos += toWrite;
                                    numBytes -= toWrite;
                                    if (outPos >= outBuffer.Length)
                                    {
                                        stdout.Write(outBuffer, 0, outPos);
                                        stdout.Flush();
                                        outPos = 0;
                                    }
                                }
                            }
                            else
                            {
                                if (isFloat)
                                {
                                    float[] floatBuffer = new float[numFrames * channels];
                                    Marshal.Copy(pData, floatBuffer, 0, floatBuffer.Length);

                                    for (int f = 0; f < numFrames; f++)
                                    {
                                        float sum = 0f;
                                        int baseIdx = f * channels;
                                        for (int c = 0; c < channels; c++)
                                        {
                                            sum += floatBuffer[baseIdx + c];
                                        }
                                        float mono = sum / channels;
                                        if (mono > 1.0f) mono = 1.0f;
                                        else if (mono < -1.0f) mono = -1.0f;
                                        short s = (short)(mono * 32767f);

                                        outBuffer[outPos++] = (byte)(s & 0xFF);
                                        outBuffer[outPos++] = (byte)((s >> 8) & 0xFF);

                                        if (outPos >= outBuffer.Length)
                                        {
                                            stdout.Write(outBuffer, 0, outPos);
                                            stdout.Flush();
                                            outPos = 0;
                                        }
                                    }
                                }
                                else if (format.wBitsPerSample == 16)
                                {
                                    short[] shortBuffer = new short[numFrames * channels];
                                    Marshal.Copy(pData, shortBuffer, 0, shortBuffer.Length);

                                    for (int f = 0; f < numFrames; f++)
                                    {
                                        int sum = 0;
                                        int baseIdx = f * channels;
                                        for (int c = 0; c < channels; c++)
                                        {
                                            sum += shortBuffer[baseIdx + c];
                                        }
                                        short s = (short)(sum / channels);

                                        outBuffer[outPos++] = (byte)(s & 0xFF);
                                        outBuffer[outPos++] = (byte)((s >> 8) & 0xFF);

                                        if (outPos >= outBuffer.Length)
                                        {
                                            stdout.Write(outBuffer, 0, outPos);
                                            stdout.Flush();
                                            outPos = 0;
                                        }
                                    }
                                }
                            }
                        }

                        captureClient.ReleaseBuffer(numFrames);
                        captureClient.GetNextPacketSize(out packetLength);
                    }

                    if (outPos > 0)
                    {
                        stdout.Write(outBuffer, 0, outPos);
                        stdout.Flush();
                        outPos = 0;
                    }
                    Thread.Sleep(10);
                }
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine("ERR: " + ex.Message);
            }
        }
    }
}
