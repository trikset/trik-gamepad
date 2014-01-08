module com.trik.gamepad.Transmitter
open Android.Widget
open Android.Content
open Android.App
open Android.Util
open System

[<assembly: UsesPermission("android.permission.INTERNET")>]
[<assembly: UsesPermission("android.permission.VIBRATE")>]
do()
let private SOS = [| 0L; 50L; 50L; 50L; 50L; 50L; 
            100L; 200L; 50L; 200L; 50L; 200L;
            100L;  50L; 50L; 50L; 50L; 50L|]

type Message = Send of string 

let create (context:Context) =
    let handler = new Android.OS.Handler(context.MainLooper)
    let toast (msg : string) =  handler.Post(Toast.MakeText(context, msg, ToastLength.Long).Show) |> ignore

    let vibrator : Android.OS.Vibrator = downcast context.GetSystemService Context.VibratorService

    fun (host, port) ->  
    MailboxProcessor.Start <| fun input ->
        let rec reconnect k =
            async { 
                try 
                    let socket = new Net.Sockets.TcpClient(host, port, NoDelay = true, SendTimeout = 5000)
                    return! k <| new IO.StreamWriter(socket.GetStream(), AutoFlush = true)
                with e ->
                    toast <| sprintf "Connection to '%s:%d' failed. %s" host port e.Message 
                    do! Async.Sleep 1000
                    return! reconnect k 
                } 
        and transmitter stream =
            let rec loop() = async {
                let send (s:string) (stream:IO.StreamWriter) = 
                        stream.WriteLine s 
                        vibrator.Vibrate 10L
                        loop()

                let! cmd = input.Receive()
                match cmd with 
                    | Send s ->
                        try 
                            return! send s stream
                        with
                            e -> Log.Error("TCP", "Failed: {0}, Trouble : {1} ", s, e.Message) |> ignore
                                 toast "Disconnected."
                                 vibrator.Vibrate(SOS, -1)
                                 return! reconnect (send s)
            }
            loop()
        reconnect transmitter
 