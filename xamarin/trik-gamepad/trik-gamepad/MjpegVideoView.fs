// Based on http://channel9.msdn.com/coding4fun/articles/MJPEG-Decoder
namespace com.trik.gamepad

open System
open System.Collections.Generic

type MjpegStream() = 
    let _header = [| 255uy;  216uy |]
    let mutable _streamActive = false
    let frameReadyEvent = new Event<_>()
    let errorEvent = new Event<_>()

    let findIndexSubrange (buf:_[]) offset size (search:_[]) = 
        let rec loop from =
            let next = Array.IndexOf (buf, search.[0], from)
            if next = -1 ||  size <= next + search.Length then -1
            else 
                let rec loop2 i =
                    if i = search.Length then next 
                    elif buf.[next + i] = search.[i] then 
                        loop2 (i + 1)
                    else loop (next + 1)                  
                loop2 1
        loop offset
            

    member val Uri : string = "" with get, set

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


    member this.OnGetResponse asyncResult =  
        let request = asyncResult.AsyncState :?> Net.HttpWebRequest
        let result = new List<_>(2000000)
        try
            let response = request.EndGetResponse asyncResult :?> Net.HttpWebResponse
            use stream = response.GetResponseStream()
            _streamActive <- true

            let buf = Array.zeroCreate 10000
            let bufLen = stream.Read(buf, 0, 100)
            let chunkSeparator = 
                Text.Encoding.UTF8.GetBytes "Content-Type: image/jpeg" 
                |> findIndexSubrange buf 0 bufLen
                |> Array.sub buf 0 

            let rec extractFrameLoop ofs = 
                if not _streamActive then () else 
                    
                let bufLen = ofs + stream.Read(buf, ofs, buf.Length - ofs) 
                let num = findIndexSubrange buf 0 bufLen _header
                if num <> -1 then                     
                    result.AddRange <| new ArraySegment<_>(buf, num, bufLen - num)
                    let rec loop() =
                        let bufLen = stream.Read (buf, 0, buf.Length)
                        let endOfChunk = findIndexSubrange buf 0 bufLen chunkSeparator
                        if endOfChunk <> -1 then endOfChunk
                        else 
                            result.AddRange <| new ArraySegment<_> (buf, 0, bufLen)
                            loop()
                    let tailLen = loop()
                    result.AddRange <| new ArraySegment<_> (buf, 0, tailLen)
                    frameReadyEvent.Trigger <| result.ToArray()
                    result.Clear()   
                    let bufLen = bufLen - tailLen
                    Array.Copy (buf, tailLen, buf, 0, bufLen)
                    extractFrameLoop bufLen
            extractFrameLoop bufLen
            response.Close()         
        with e->  errorEvent.Trigger e
    
//open Android.App
//open Android.Contenta
//open Android.OS
//open Android.Runtime
//open Android.Util
//open Android.Views
//open Android.Widget
//
//
//
//[<AllowNullLiteral>]
//type MjpegVideo (context:Context, attrs:IAttributeSet, defStyle:int) as this =
//    inherit SurfaceView(context, attrs, defStyle)
//    let processor = new MjpegDecoder()
//    do processor.FrameReady.Add <| fun frameData -> 
//        fun () -> ()
//        |> this.Post |> ignore  
//
//    member val Uri = "" with get, set
//
//    member this.SetVideoURI uri =  
//        this.Uri <- uri
//        processor.ParseStream this.Uri
//
//    //member this.StopPlayback () = processor.StopStream()
//
//    //member this.Resume () = processor.ParseStream this.Uri
//
//    interface ISurfaceHolderCallback with
//        member this.SurfaceCreated holder = ()
//        member this.SurfaceDestroyed holder = ()
//        member this.SurfaceChanged (holder, format, width, height) = ()
//
//
