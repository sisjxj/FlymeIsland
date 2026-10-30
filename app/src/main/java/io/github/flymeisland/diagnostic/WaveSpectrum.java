package io.github.flymeisland.diagnostic;
/** Converts a short bounded PCM/FFT snapshot to five coarse frequency levels. */
public final class WaveSpectrum {
    private static final int[][] FREQ={{80,140,200},{250,350,500},{600,850,1200},{1400,2000,2800},{3200,4500,6500}};
    private WaveSpectrum() {}
    public static byte[] pcm(short[] input,int count,int rate) {
        byte[] out=new byte[5];
        if(input==null || count<256 || rate<8000) return out;
        count=Math.min(count,input.length);
        double[] window=new double[count];
        for(int i=0;i<count;i++) window[i]=.5-.5*Math.cos(2*Math.PI*i/Math.max(1,count-1));
        for(int b=0;b<5;b++) {
            double energy=0;
            for(int hz:FREQ[b]) {
                double omega=2*Math.PI*hz/rate, coeff=2*Math.cos(omega), q0=0,q1=0,q2=0;
                for(int i=0;i<count;i++) {
                    q0=(input[i]/32768d)*window[i]+coeff*q1-q2;q2=q1;q1=q0;
                }
                double magnitude=Math.sqrt(Math.max(0,q1*q1+q2*q2-coeff*q1*q2))*4d/count;
                energy+=magnitude*magnitude;
            }
            int level=(int)Math.round(Math.min(255,Math.sqrt(energy/FREQ[b].length)*750));
            out[b]=(byte)level;
        }
        return out;
    }
    public static byte[] fft(byte[] fft,int samplingRateMilliHz) {
        byte[] out=new byte[5];
        if(fft==null || fft.length<8 || samplingRateMilliHz<=0) return out;
        double resolution=(samplingRateMilliHz/1000d)/fft.length;
        for(int b=0;b<5;b++) {
            double sum=0;int count=0;
            for(int hz:FREQ[b]) {
                int bin=Math.max(1,Math.min(fft.length/2-1,(int)Math.round(hz/resolution)));
                int re=fft[2*bin], im=fft[2*bin+1];sum+=re*re+im*im;count++;
            }
            out[b]=(byte)Math.round(Math.min(255,Math.sqrt(sum/Math.max(1,count))*2));
        }
        return out;
    }
}
