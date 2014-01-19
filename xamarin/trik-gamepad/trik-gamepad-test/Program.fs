
// NOTE: If warnings appear, you may need to retarget this project to .NET 4.0. Show the Solution
// Pad, right-click on the project node, choose 'Options --> Build --> General' and change the target
// framework to .NET 4.0 or .NET 4.5.

module trik.gamepad.test.Main
open System
open System.Reactive
open System.Reactive.Linq
let url = "http://192.168.51.7:8889/s"
let viewer = new com.trik.gamepad.MjpegStream(Uri = url)
let cnt = ref 0
let prev = ref DateTime.Now

viewer.FrameReady.Add <| fun data ->
        lock cnt <| fun () -> 
        incr cnt
        let now = DateTime.Now
        let elapsed = now - !prev
        printfn "%d %d" !cnt elapsed.Milliseconds
        let stream = new IO.BinaryWriter(IO.File.Create <| sprintf "out_%d.jpg" !cnt) 
        stream.Write data
        stream.Close()
        prev := now

       
viewer.Error.Add <| printfn "%A"

let ok = viewer.StartAsync()
Console.ReadKey () |> ignore 