// Based on http://channel9.msdn.com/coding4fun/articles/MJPEG-Decoder
namespace com.trik.gamepad

open System
open System.Collections.Generic

type MjpegStream() = 
    let _header = [| 255uy;  216uy |]
    let mutable _streamActive = false
    let frameReadyEvent = new Event<_>()
    let errorEvent = new Event<_>()
    let completedEvent = new Event<_>()

    let mutable _uri = ""

    member this.Uri 
        with get () = _uri and 
             set v = if _uri <> v then
                         _uri <- v
                         if _streamActive then 
                            this.StopAsync() // TODo: fix it ...
                            this.StartAsync() |> ignore

    member this.StartAsync() =
        try
            let request = Net.WebRequest.Create this.Uri
            let result = request.BeginGetResponse (new AsyncCallback(this.OnGetResponse), request)
            None
        with e -> Some e.Message
    
    member this.StopAsync() = _streamActive <- false     

    [<CLIEvent>]
    member this.FrameReady = frameReadyEvent.Publish 
    [<CLIEvent>]
    member this.Error = errorEvent.Publish 
    [<CLIEvent>]
    member this.Completed = completedEvent.Publish 
   

    member this.OnGetResponse asyncResult =  
        let request = asyncResult.AsyncState :?> Net.HttpWebRequest
        try
            let response = request.EndGetResponse asyncResult :?> Net.HttpWebResponse
            use stream = response.GetResponseStream()
            //use reader = new IO.StreamReader(, Text.Encoding.UTF8,  false, 100000)
            _streamActive <- true

            let readLine () = 
                let r = new List<_>(80)
                let rec loop () = 
                    let b = stream.ReadByte()
                    if b < 0 then
                        completedEvent.Trigger ()
                    elif b = 13 then 
                        let lf = stream.ReadByte()
                        assert (lf = 10)
                    else r.Add <| byte b; loop()
                loop ()
                r.ToArray() |> Text.Encoding.UTF8.GetString

            let inline assertThat name v = if not v then raise <| invalidOp "'%s' failed" name 

            let eoh = [|13;10;13;10|]
            let rec extractFrame () =
                if not _streamActive  then () else

                let parseHeader () = 
                    let empty1 = readLine ()
                    assertThat "E1" <| String.IsNullOrEmpty empty1

                    let boundary = readLine ()
                    assertThat "Boundary" <| boundary.StartsWith "--"

                    let contentType = readLine ()
                    assertThat "Content-Type" <| contentType.StartsWith("Content-Type", StringComparison.InvariantCultureIgnoreCase)

                    let contentLengthStr = readLine ()
                    assertThat "Content-Length" <|  contentLengthStr.StartsWith("Content-Length", StringComparison.InvariantCultureIgnoreCase)

                    let (status, contentLength) = Int32.TryParse (contentLengthStr.Split[|':'|]).[1]
                    assertThat "Content-Length value" <| (status && contentLength > 0)

                    let empty2 = readLine ()
                    assertThat "E2" <| String.IsNullOrEmpty empty2

                    contentLength                

                let extractData len = 
                    let buf = Array.zeroCreate len 
                    let rec readData rest =
                        if rest = 0 then 0 else
                        let cnt = stream.Read(buf, 0, rest)
                        if cnt = 0 then rest else
                        readData <| rest - cnt
                    let rest = readData len
                    assertThat "Data read" <| (rest = 0)
                    buf

                let buf = extractData <|  parseHeader () 
                assertThat "JPG header" <| (buf.[0] = _header.[0] && buf.[1] = _header.[1])
                let bmp = Android.Graphics.BitmapFactory.DecodeByteArrayAsync (buf, 0, buf.Length)
                frameReadyEvent.Trigger bmp
                extractFrame()
            extractFrame ()
            response.Close() 
            completedEvent.Trigger ()        
        with e -> errorEvent.Trigger e
